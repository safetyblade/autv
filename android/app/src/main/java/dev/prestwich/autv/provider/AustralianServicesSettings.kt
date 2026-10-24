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
    var selected by rememberSaveable { mutableStateOf<String?>(model.pendingActivationProvider) }
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
                                        Text(when (service.id) { "9now" -> connectionLabel(model.providerConnection); "sbs" -> connectionLabel(model.sbsAccount.connection); "seven" -> connectionLabel(model.sevenAccount.connection); else -> "Not available yet" },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (service.id in setOf("9now", "sbs", "seven")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        val service = services.first { it.id == selected }
                        TextButton(onClick = { selected = null }, modifier = Modifier.focusRequester(firstFocus).testTag("providers-back")) { Text("‹ All services") }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ServiceIcon(service)
                            Column {
                                Text(service.name, style = MaterialTheme.typography.headlineSmall)
                                Text(service.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (service.id !in setOf("9now", "sbs", "seven")) {
                            Text("Not available yet", style = MaterialTheme.typography.titleMedium)
                            Text("${service.name} account connections are not supported in AUTV yet. Your existing guide channels remain available.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val sbs = service.id == "sbs"
                            val account = when (service.id) { "sbs" -> model.sbsAccount; "seven" -> model.sevenAccount; else -> null }
                            val connection = if (account != null) account.connection else model.providerConnection
                            val activation = if (account != null) account.activation else model.providerActivation
                            val busy = if (account != null) account.busy else model.providerBusy
                            val count = if (account != null) account.channels.size else model.providerChannelCount
                            val status = if (account != null) account.status else model.providerAuthStatus
                            val error = if (account != null) account.error else model.providerError
                            val connect: () -> Unit = if (account != null) account::connect else model::connectNineNow
                            val disconnect: () -> Unit = if (account != null) account::disconnect else model::disconnectNineNow
                            val refresh: () -> Unit = if (account != null) account::refresh else model::refreshProviderChannels
                            Text(connectionLabel(connection), color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("${service.id}-connection-state"))
                            Text(if (connection == ProviderConnection.CONNECTED)
                                if (service.id == "seven") "$count channels listed. Native 7plus playback is not available yet." else "$count live channels and events in your guide."
                                else "Connect your ${service.name} account to bring its live channels into AUTV.")
                            if (service.id == "seven") Text("Experimental account linking and channel discovery. Playback is unavailable while licence and advertising support is being developed.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (sbs) Text("Experimental native HLS. Some channels may require an unsupported ad-session or protection path.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            activation?.let { challenge ->
                                ProviderActivationScreen(service.name, challenge, when (service.id) { "sbs" -> ActivationDestination.SBS; "seven" -> ActivationDestination.SEVEN_PLUS; else -> ActivationDestination.NINE_NOW },
                                    onOpenWebsite = { address -> runCatching { uriHandler.openUri(address) }; Unit }, onCancel = disconnect)
                            }
                            status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("${service.id}-auth-status")) }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("${service.id}-error")) }
                            if (!busy && connection != ProviderConnection.CONNECTED) {
                                Button(onClick = connect, modifier = Modifier.fillMaxWidth().testTag("${service.id}-connect")) { Text("Connect ${service.name}") }
                            }
                            if (connection == ProviderConnection.CONNECTED) {
                                Button(onClick = refresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Refreshing…" else "Refresh channels") }
                            }
                            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Available in Australia. Plays on this device; Cast is unavailable for ${service.name}. Your connection is saved securely. AUTV never asks for your password.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (activation == null && (busy || connection in setOf(ProviderConnection.CONNECTED, ProviderConnection.CONNECTING))) {
                                TextButton(onClick = disconnect, modifier = Modifier.testTag("${service.id}-disconnect")) {
                                    Text(if (connection == ProviderConnection.CONNECTED) "Sign out of ${service.name}" else "Cancel sign-in")
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
