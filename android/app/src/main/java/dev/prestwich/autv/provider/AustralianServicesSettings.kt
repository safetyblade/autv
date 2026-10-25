package dev.prestwich.autv.provider

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.prestwich.autv.PlaybackModel
import dev.prestwich.autv.R
import dev.prestwich.autv.data.provider.ProviderConnection

private data class AustralianService(val id: String, val name: String, val description: String, @DrawableRes val icon: Int?)
private val services = listOf(
    AustralianService("9now", "9Now", "Live channels & special events", R.drawable.provider_nine),
    AustralianService("abc", "ABC iview", "Australia's national broadcaster", R.drawable.provider_abc),
    AustralianService("sbs", "SBS On Demand", "Stories from around the world", R.drawable.provider_sbs),
    AustralianService("seven", "7plus", "Seven Network", R.drawable.provider_seven),
    AustralianService("ten", "10", "Network 10", R.drawable.provider_ten),
)

/** Provider credentials are entered only on the provider's supported sign-in site. */
@Composable
internal fun AustralianServicesSettings(model: PlaybackModel, onClose: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var selected by rememberSaveable { mutableStateOf<String?>(if (model.providerActivation != null) "9now" else null) }
    val firstFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    LaunchedEffect(selected) { scroll.scrollTo(0); withFrameNanos { }; firstFocus.requestFocus() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MaterialTheme(colorScheme = darkColorScheme(
            primary = Color(0xFF82DFE8), onPrimary = Color(0xFF00363C),
            surface = Color(0xFF111A25), surfaceContainer = Color(0xFF1A2635),
            onSurface = Color(0xFFF1F5FA), onSurfaceVariant = Color(0xFFB1C0CF),
        )) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color(0xFF314255)),
                modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(0.94f).heightIn(max = 720.dp).testTag("australian-services-settings")) {
                Column(Modifier.verticalScroll(scroll).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("AUTV  /  SETTINGS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Australian services", style = MaterialTheme.typography.headlineSmall)
                    if (selected == null) {
                        Text("Your networks. One guide.", style = MaterialTheme.typography.titleMedium)
                        Text("Choose a service to manage its connection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        services.forEach { service ->
                            OutlinedCard(onClick = { selected = service.id },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier.fillMaxWidth().then(if (service.id == "9now") Modifier.focusRequester(firstFocus) else Modifier).testTag("provider-${service.id}")) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    ServiceIcon(service)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(service.name, style = MaterialTheme.typography.titleMedium)
                                        Text(if (service.id == "9now") connectionLabel(model.providerConnection) else "Not available yet",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (service.id == "9now") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics {})
                                }
                            }
                        }
                    } else {
                        val service = services.first { it.id == selected }
                        TextButton(onClick = { selected = null }, modifier = Modifier.focusRequester(firstFocus).clearAndSetSemantics { contentDescription = "Back to all services" }.testTag("providers-back")) { Text("‹ All services") }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ServiceIcon(service)
                            Column {
                                Text(service.name, style = MaterialTheme.typography.headlineSmall)
                                Text(service.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (service.id != "9now") {
                            Text("Not available yet", style = MaterialTheme.typography.titleMedium)
                            Text("${service.name} account connections are not supported in AUTV yet. Your existing guide channels remain available.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(connectionLabel(model.providerConnection), color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("9now-connection-state"))
                            Text(if (model.providerConnection == ProviderConnection.CONNECTED)
                                "${model.providerChannelCount} live channels and events in your guide."
                                else "Connect your Nine account to bring its live channels and events into AUTV.")
                            model.providerActivation?.let { activation ->
                                ProviderActivationScreen("9Now", activation, ActivationDestination.NINE_NOW,
                                    onOpenWebsite = { address -> runCatching { uriHandler.openUri(address) }; Unit },
                                    onCancel = model::disconnectNineNow)
                            }
                            model.providerAuthStatus?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("9now-auth-status")) }
                            model.providerError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("9now-error")) }
                            if (!model.providerBusy && model.providerConnection != ProviderConnection.CONNECTED) {
                                Button(onClick = model::connectNineNow, modifier = Modifier.fillMaxWidth().testTag("9now-connect")) { Text("Connect 9Now") }
                            }
                            if (model.providerConnection == ProviderConnection.CONNECTED) {
                                Button(onClick = model::refreshProviderChannels, enabled = !model.providerBusy,
                                    modifier = Modifier.fillMaxWidth()) { Text(if (model.providerBusy) "Refreshing…" else "Refresh channels") }
                            }
                            if (model.providerBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Available in Australia. Plays on this device; Cast is unavailable for 9Now. Your connection is saved securely. AUTV never asks for your password.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (model.providerActivation == null && (model.providerBusy || model.providerConnection in setOf(ProviderConnection.CONNECTED, ProviderConnection.CONNECTING))) {
                                TextButton(onClick = model::disconnectNineNow, modifier = Modifier.testTag("9now-disconnect")) {
                                    Text(if (model.providerConnection == ProviderConnection.CONNECTED) "Sign out of 9Now" else "Cancel sign-in")
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFF314255))
                    TextButton(onClick = onClose, modifier = Modifier.testTag("close-settings")) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun ServiceIcon(service: AustralianService) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, modifier = Modifier.size(56.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (service.icon != null) Image(painterResource(service.icon), contentDescription = null, modifier = Modifier.fillMaxSize())
            else Text("10", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF0065AD))
        }
    }
}

private fun connectionLabel(connection: ProviderConnection): String = when (connection) {
    ProviderConnection.NOT_CONNECTED -> "Not connected"
    ProviderConnection.CONNECTING -> "Waiting for sign-in"
    ProviderConnection.CONNECTED -> "Connected"
    ProviderConnection.SESSION_EXPIRED -> "Reconnect to continue"
    ProviderConnection.UNSUPPORTED -> "Unavailable in this build"
}
