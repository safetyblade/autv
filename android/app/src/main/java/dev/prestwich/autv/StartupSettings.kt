package dev.prestwich.autv

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.prestwich.autv.guide.*

@Composable
internal fun StartupSettings(model: PlaybackModel) {
    var open by remember { mutableStateOf(false) }
    val settings = model.startupPreferences
    Text("Playback", style = MaterialTheme.typography.titleLarge)
    FocusButton("Startup channel: ${settings.mode.label}", { open = !open }, Modifier.fillMaxWidth().testTag("startup-channel"))
    if (open) {
        StartupMode.entries.forEach { mode ->
            FocusButton((if (settings.mode == mode) "✓ " else "") + mode.label, { settings.choose(mode) }, Modifier.fillMaxWidth().testTag("startup-${mode.name}"))
        }
        if (settings.mode in setOf(StartupMode.AUSTRALIAN, StartupMode.NEWS, StartupMode.SPORTS)) {
            Text("Choose a channel", style = MaterialTheme.typography.titleMedium)
            FocusButton("Automatic", { settings.chooseChannel(null) })
            val rows = StartupChannels.choices(model.guide?.channels.orEmpty(), settings.mode)
            if (rows.isEmpty()) Text("No channels available. Connect Australian services or refresh the guide.")
            if (settings.identity != null && rows.none { FavouriteIdentity.key(it) == settings.identity })
                Text("Your saved channel is currently unavailable. It will be kept for when it returns.")
            rows.forEach { channel ->
                FocusButton((if (settings.identity == FavouriteIdentity.key(channel)) "✓ " else "") + channel.name,
                    { settings.chooseChannel(FavouriteIdentity.key(channel)) }, Modifier.fillMaxWidth())
            }
        }
        Text("Unavailable channels are skipped if playback fails. You can always choose another channel from the guide.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
    }
}
