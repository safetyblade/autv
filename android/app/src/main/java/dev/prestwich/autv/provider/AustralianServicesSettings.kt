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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
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
                            val statusText = when {
                                service.id == "9now" -> connectionLabel(model.providerConnection)
                                model.providerAccounts[service.id] != null -> model.providerAccounts.getValue(service.id).let {
                                    if (it.anonymousLiveApproved && it.connection != ProviderConnection.CONNECTED) "Live TV without account" else connectionLabel(it.connection)
                                }
                                else -> "Connection not configured"
                            }
                            OutlinedCard(onClick = { selected = service.id },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier.fillMaxWidth().then(if (service.id == "9now") Modifier.focusRequester(firstFocus) else Modifier)
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "${service.name}, ${service.description} · $statusText"
                                    }
                                    .testTag("provider-${service.id}")) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    ServiceIcon(service)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(service.name, style = MaterialTheme.typography.titleMedium)
                                        Text(statusText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (model.providerUnavailableReason(service.id) == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics {})
                                }
                            }
                        }
                    } else {
                        val service = services.first { it.id == selected }
                        TextButton(onClick = { selected = null }, modifier = Modifier.focusRequester(firstFocus).semantics { contentDescription = "Back to all services" }.testTag("providers-back")) { Text("‹ All services", modifier = Modifier.clearAndSetSemantics {}) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ServiceIcon(service)
                            Column {
                                Text(service.name, style = MaterialTheme.typography.headlineSmall)
                                Text(service.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val unavailable=model.providerUnavailableReason(service.id)
                        if (unavailable!=null) {
                            Text("Connection not enabled",style=MaterialTheme.typography.titleMedium)
                            Text("This service is not enabled in this build. Your saved account has not been removed.",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("${service.id}-unavailable"))
                        } else {
                            val account=model.providerAccounts[service.id]
                            val connection=account?.connection ?: model.providerConnection
                            val activation=account?.activation ?: if(service.id=="9now") model.providerActivation else null
                            val busy=account?.busy ?: model.providerBusy
                            val count=account?.channels?.size ?: model.providerChannelCount
                            val status=account?.status ?: if(service.id=="9now") model.providerAuthStatus else null
                            val error=account?.error ?: if(service.id=="9now") model.providerError else null
                            val canBrowse=account?.canBrowse ?: (connection==ProviderConnection.CONNECTED)
                            val connect: ()->Unit=account?.let { it::connect } ?: model::connectNineNow
                            val disconnect: ()->Unit=account?.let { it::disconnect } ?: model::disconnectNineNow
                            val refresh: ()->Unit=account?.let { it::refresh } ?: model::refreshProviderChannels
                            Text(if(account?.anonymousLiveApproved==true && connection!=ProviderConnection.CONNECTED) "Live TV available without an account" else connectionLabel(connection),
                                color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("${service.id}-connection-state"))
                            Text(if(canBrowse) "$count live channels in your guide." else "Connect your ${service.name} account to add its live channels.")
                            activation?.let { challenge ->
                                val destination=when(service.id) { "seven"->ActivationDestination.SEVEN;"sbs"->ActivationDestination.SBS;"ten"->ActivationDestination.TEN;"abc"->ActivationDestination.ABC;else->ActivationDestination.NINE_NOW }
                                ProviderActivationScreen(service.name,challenge,destination,
                                    onOpenWebsite={ address -> runCatching { uriHandler.openUri(address) };Unit },onCancel=disconnect)
                            }
                            status?.let { Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("${service.id}-auth-status")) }
                            error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("${service.id}-error")) }
                            if(!busy && connection!=ProviderConnection.CONNECTED && account?.accountLinkingEnabled!=false) {
                                Button(onClick=connect,modifier=Modifier.fillMaxWidth().testTag("${service.id}-connect")) { Text("Connect ${service.name}") }
                            }
                            if(canBrowse) Button(onClick=refresh,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("${service.id}-refresh")) { Text(if(busy) "Refreshing…" else "Refresh channels") }
                            if(busy) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                            Text("Available in Australia. Native playback on this device; Cast is unavailable. Your connection is saved securely. AUTV never asks for your password.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(activation==null && (busy || connection in setOf(ProviderConnection.CONNECTED,ProviderConnection.CONNECTING))) {
                                TextButton(onClick=disconnect,modifier=Modifier.testTag("${service.id}-disconnect")) { Text(if(connection==ProviderConnection.CONNECTED) "Sign out of ${service.name}" else "Cancel sign-in") }
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
