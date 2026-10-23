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
import androidx.compo…1445 tokens truncated…Variant)
                            }
                        }
                        if (service.id !in setOf("9now", "sbs", "seven")) {
                            Text("Not available yet", style = MaterialTheme.typography.titleMedium)
                            Text("${service.name} account connections are not supported in AUTV yet. Your existing guide channels remain available.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val sbs = service.id == "sbs"
                            val account = when (service.id) { "sbs" -> model.sbsAccount; "seven" -> model.sevenAccount; else -> null }
                            val connection = account?.connection ?: model.providerConnection
                            val activation = if (account != null) account.activation else model.providerActivation
                            val busy = account?.busy ?: model.providerBusy
                            val count = account?.channels?.size ?: model.providerChannelCount
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
