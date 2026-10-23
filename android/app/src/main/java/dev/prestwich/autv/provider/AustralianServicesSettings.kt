package dev.prestwich.autv.provider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.prestwich.autv.PlaybackModel
import dev.prestwich.autv.data.provider.ProviderConnection

/** Account UI only; provider channels use the existing guide and retained playback engine. */
@Composable
internal fun AustralianServicesSettings(model: PlaybackModel, onClose: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; closeFocus.requestFocus() }
    Dialog(onDismissRequest = onClose) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().testTag("australian-services-settings")) {
                Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall)
                    Text("Australian services", style = MaterialTheme.typography.titleLarge)
                    Text("9Now · Experimental", style = MaterialTheme.typography.titleMedium)
                    Text(when (model.providerConnection) {
                        ProviderConnection.NOT_CONNECTED -> "Not connected"
                        ProviderConnection.CONNECTING -> "Waiting for sign-in"
                        ProviderConnection.CONNECTED -> "Connected"
                        ProviderConnection.SESSION_EXPIRED -> "Session expired"
                        ProviderConnection.UNSUPPORTED -> "Unsupported"
                    }, modifier = Modifier.testTag("9now-connection-state"))
                    Text("Connect your Nine account to add Seinfeld 24/7 to the guide. Requires an Australian network. Native playback is being tested.")
                    model.providerActivation?.let { activation ->
                        Text("On your phone or computer, visit 9now.com.au/activate and sign in with Nine.")
                        // Activation codes are displayed only here, never in semantics logs or storage.
                        Text(activation.userCode, style = MaterialTheme.typography.headlineMedium)
                        Text("Enter this code to connect this device. AUTV does not ask for your password.")
                        Text("After submitting it, switch back to AUTV to finish connecting. You can also enter the code on another device while AUTV stays open.")
                        TextButton(onClick = { runCatching { uriHandler.openUri(activation.verificationUrl) } }) {
                            Text("Open Nine sign-in")
                        }
                    }
                    model.providerAuthStatus?.let { Text(it, modifier = Modifier.testTag("9now-auth-status")) }
                    model.providerError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("9now-error")) }
                    if (!model.providerBusy && model.providerConnection != ProviderConnection.CONNECTED) {
                        Button(onClick = model::connectNineNow, modifier = Modifier.testTag("9now-connect")) { Text("Connect 9Now") }
                    }
                    if (model.providerConnection == ProviderConnection.CONNECTED) {
                        Button(onClick = model::refreshProviderChannels, enabled = !model.providerBusy) { Text("Refresh channels") }
                        Text("Seinfeld 24/7 appears under Australian TV when available. Cast is unavailable for 9Now.")
                    }
                    if (model.providerBusy || model.providerConnection == ProviderConnection.CONNECTED ||
                        model.providerConnection == ProviderConnection.CONNECTING) {
                        TextButton(onClick = model::disconnectNineNow, modifier = Modifier.testTag("9now-disconnect")) {
                            Text(if (model.providerConnection == ProviderConnection.CONNECTED) "Sign out" else "Cancel sign-in")
                        }
                    }
                    TextButton(onClick = onClose, modifier = Modifier.focusRequester(closeFocus).testTag("close-settings")) { Text("Close") }
                }
            }
        }
    }
}
