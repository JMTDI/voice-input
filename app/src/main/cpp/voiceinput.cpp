#include <string>
#include <jni.h>
#include <bits/sysconf.h>
#include "ggml/whisper.h"
#include "defines.h"
#include "voiceinput.h"
#include "jni_common.h"

std::string jstring2string(JNIEnv *env, jstring jStr) {
    const jsize stringUtf8Length = env->GetStringUTFLength(jStr);
    if (stringUtf8Length <= 0) {
        AKLOGE("Can't get jStr");
        return "";
    }
    char stringChars[stringUtf8Length + 1];
    env->GetStringUTFRegion(jStr, 0, env->GetStringLength(jStr), stringChars);
    stringChars[stringUtf8Length] = '\0';

    return {stringChars};
}


struct WhisperModelState {
    JNIEnv *env;
    jobject partial_result_instance;
    jmethodID partial_result_method;
    int n_threads = 4;
    struct whisper_context *context = nullptr;
};

static jlong WhisperGGML_open(JNIEnv *env, jclass clazz, jstring model_dir) {
    std::string model_dir_str = jstring2string(env, model_dir);

    auto *state = new WhisperModelState();

    state->context = whisper_init_from_file(model_dir_str.c_str());

    if(!state->context){
        AKLOGE("Failed to initialize whisper_context from path %s", model_dir_str.c_str());
        delete state;
        return 0L;
    }

    return reinterpret_cast<jlong>(state);
}

static jlong WhisperGGML_openFromBuffer(JNIEnv *env, jclass clazz, jobject buffer) {
    void* buffer_address = env->GetDirectBufferAddress(buffer);
    jlong buffer_capacity = env->GetDirectBufferCapacity(buffer);

    auto *state = new WhisperModelState();

    state->context = whisper_init_from_buffer(buffer_address, buffer_capacity);

    if(!state->context){
        AKLOGE("Failed to initialize whisper_context from direct buffer");
        delete state;
        return 0L;
    }

    return reinterpret_cast<jlong>(state);
}

static jstring WhisperGGML_infer(JNIEnv *env, jobject instance, jlong handle, jfloatArray samples_array, jstring prompt) {
    auto *state = reinterpret_cast<WhisperModelState *>(handle);

    size_t num_samples = env->GetArrayLength(samples_array);
    jfloat *samples = env->GetFloatArrayElements(samples_array, nullptr);

    bool use_beam_search = false;

    long num_procs = sysconf(_SC_NPROCESSORS_ONLN);
    if(num_procs < 2 || num_procs > 16) num_procs = 6; // Make sure the number is sane

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false;
    wparams.print_realtime = false;
    wparams.print_special = false;
    wparams.print_timestamps = false;
    wparams.max_tokens = 256;
    wparams.n_threads = (int)num_procs;

    //wparams.audio_ctx = (int)ceil((double)num_samples / (double)(160.0 * 2.0));
    wparams.temperature_inc = 0.0f;

    // Replicates old tflite behavior
    if(!use_beam_search) {
        wparams.strategy = WHISPER_SAMPLING_GREEDY;
        wparams.greedy.best_of = 1;
    } else {
        wparams.strategy = WHISPER_SAMPLING_BEAM_SEARCH;
        wparams.beam_search.beam_size = 5;
        wparams.greedy.best_of = 5;
    }


    wparams.suppress_blank = false;
    wparams.suppress_non_speech_tokens = true;
    wparams.no_timestamps = true;


    std::string prompt_str = jstring2string(env, prompt);
    wparams.initial_prompt = prompt_str.c_str();
    AKLOGI("Initial prompt is [%s]", prompt_str.c_str());

    state->env = env;
    state->partial_result_instance = instance;
    state->partial_result_method = env->GetMethodID(
            env->GetObjectClass(instance),
            "invokePartialResult",
            "(Ljava/lang/String;)V");

    wparams.partial_text_callback_user_data = state;
    wparams.partial_text_callback = [](struct whisper_context * ctx, struct whisper_state * state, const whisper_token_data *tokens, size_t n_tokens, void * user_data) {
        std::string partial;
        for(size_t i=0; i < n_tokens; i++) {
            if(tokens[i].id == whisper_token_beg(ctx) ||
                tokens[i].id == whisper_token_eot(ctx) ||
                tokens[i].id == whisper_token_nosp(ctx) ||
                tokens[i].id == whisper_token_not(ctx) ||
                tokens[i].id == whisper_token_prev(ctx) ||
                tokens[i].id == whisper_token_solm(ctx) ||
                tokens[i].id == whisper_token_sot(ctx) ||
                tokens[i].id == whisper_token_transcribe(ctx) ||
                tokens[i].id == whisper_token_translate(ctx)) continue;

            partial += whisper_token_to_str(ctx, tokens[i].id);
        }

        auto *wstate = reinterpret_cast<WhisperModelState *>(user_data);

        jstring pjstr = wstate->env->NewStringUTF(partial.c_str());
        wstate->env->CallVoidMethod(wstate->partial_result_instance, wstate->partial_result_method, pjstr);
        // TODO: Delete local ref
    };

    AKLOGI("Calling whisper_full");
    int res = whisper_full(state->context, wparams, samples, (int)num_samples);
    if(res != 0) {
        AKLOGE("WhisperGGML whisper_full failed with non-zero code %d", res);
    }
    AKLOGI("whisper_full finished");

    whisper_print_timings(state->context);

    std::string output = "";
    const int n_segments = whisper_full_n_segments(state->context);

    for (int i = 0; i < n_segments; i++) {
        auto seg = whisper_full_get_segment_text(state->context, i);
        output.append(seg);
    }

    jstring jstr = env->NewStringUTF(output.c_str());
    return jstr;
}

static void WhisperGGML_close(JNIEnv *env, jclass clazz, jlong handle) {
    auto *state = reinterpret_cast<WhisperModelState *>(handle);
    if(!state) return;

    delete state;
}


static const JNINativeMethod sMethods[] = {
        {
                const_cast<char *>("openNative"),
                const_cast<char *>("(Ljava/lang/String;)J"),
                reinterpret_cast<void *>(WhisperGGML_open)
        },
        {
                const_cast<char *>("openFromBufferNative"),
                const_cast<char *>("(Ljava/nio/Buffer;)J"),
                reinterpret_cast<void *>(WhisperGGML_openFromBuffer)
        },
        {
                const_cast<char *>("inferNative"),
                const_cast<char *>("(J[FLjava/lang/String;)Ljava/lang/String;"),
                reinterpret_cast<void *>(WhisperGGML_infer)
        },
        {
                const_cast<char *>("closeNative"),
                const_cast<char *>("(J)V"),
                reinterpret_cast<void *>(WhisperGGML_close)
        }
};

int register_WhisperGGML(JNIEnv *env) {
    const char *const kClassPathName = "org/futo/voiceinput/ggml/WhisperGGML";
    return registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
}