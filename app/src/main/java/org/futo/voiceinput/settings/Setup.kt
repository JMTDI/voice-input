package org.futo.voiceinput.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.futo.voiceinput.R
import org.futo.voiceinput.theme.Typography


@Composable
fun SetupContainer(inner: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.75f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterVertically)
                    .padding(32.dp)
            ) {
                Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                    Column {
                        inner()
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.25f))
    }
}

@Composable
fun Step(fraction: Float, text: String) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text, style = Typography.labelSmall)
        LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth())
    }
}

fun openImeOptions(context: Context) {
    // TODO: look into direct boot to get rid of direct boot warning?
    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)

    intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK
            or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            or Intent.FLAG_ACTIVITY_NO_HISTORY
            or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

    context.startActivity(intent)
    // TODO: I believe some keyboards seem to poll repeatedly to see if it got enabled yet,
    // to exit settings and go back to the app, not requiring the user to press back
}

// TODO: May wish to have a skip option
@Composable
@Preview
fun SetupEnableIME(onClick: () -> Unit = { }) {
    val context = LocalContext.current

    val launchImeOptions = {
        openImeOptions(context)
        onClick()
    }

    SetupContainer {
        Step(fraction = 0.33f, text = stringResource(R.string.step_1_of_2))

        Text(
            stringResource(R.string.enable_ime_body),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = launchImeOptions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.open_input_method_settings))
        }
    }
}

@Composable
@Preview
fun SetupEnableMic(onClick: () -> Unit = { }) {
    val context = LocalContext.current

    var askedCount by remember { mutableStateOf(0) }
    val askMicAccess = {
        if (askedCount++ >= 2) {
            val packageName = context.packageName
            val myAppSettings = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(
                    "package:$packageName"
                )
            )
            myAppSettings.addCategory(Intent.CATEGORY_DEFAULT)
            myAppSettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(myAppSettings)
        } else {
            (context as SettingsActivity).requestPermission()
        }
        onClick()
    }

    SetupContainer {
        Step(fraction = 0.66f, text = stringResource(R.string.step_2_of_2))
        // TODO: Include some privacy statement
        Text(
            stringResource(R.string.grant_microphone_body),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = askMicAccess,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.grant_microphone_permission))
        }
    }
}


@Composable
@Preview
fun SetupBlacklistedKeyboardWarning(
    info: BlacklistedInputMethod = BlacklistedInputMethod(
        "sample",
        "Example Keyboard is not compatible",
        "Let me continue"
    ), onClick: () -> Unit = { }
) {

    SetupContainer {
        Text(
            stringResource(R.string.incompatible_keyboard),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            info.details,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
            style = Typography.bodyMedium
        )
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(info.dismiss)
        }
    }
}

@Composable
@Preview
fun SetupWrongDefaultWarning(
    info: DefaultVoiceInputIntent = DefaultVoiceInputIntent(
        kind = DefaultVoiceInputIntentKind.OTHER,
        name = ComponentName.unflattenFromString("com.google/.tts")
    ),
    onClick: () -> Unit = { }
) {
    SetupContainer {
        Text(
            stringResource(R.string.change_default_voice_input),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            stringResource(R.string.wrong_default_body, info.name!!.packageName),
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
            style = Typography.bodyMedium
        )

        val context = LocalContext.current
        Row {
            Button(
                onClick = { context.openSystemDefaultsSettings(info.name) },
                modifier = Modifier
                    .weight(2.0f)
                    .padding(6.dp, 16.dp)
            ) {
                Text(stringResource(R.string.clear_app_s_defaults))
            }
            Button(
                onClick = onClick,
                modifier = Modifier
                    .weight(1.5f)
                    .padding(6.dp, 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }
}
