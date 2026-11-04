package dev.prestwich.autv

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.prestwich.autv.guide.*

@Composable
internal fun StartupSettings(model: PlaybackModel) {
    var open by remember { mutableStateOf(false) }
    val settings = model.startupPreferences
    Text("Playback", style = MaterialTheme.typography.titleLarge)
    FocusButton("Startup channel: ${settings.mode.label}", { open = !open }, Modifier.fillMaxWidth().testTag("startup-channel")
        .semantics { contentDescription = "Startup channel: ${settings.mode.label}. ${if (open) "Collapse options" else "Expand options"}" })
    if (open) {
        StartupMode.entries.forEach { mode ->
            val isSelected = settings.mode == mode
            FocusButton((if (isSelected) "✓ " else "") + mode.label, { settings.choose(mode) },
                Modifier.fillMaxWidth().semantics {
                    this.selected = isSelected
                    this.role = Role.RadioButton
                    contentDescription = "${mode.label}${if (isSelected) ", selected" else ""}"
                }.testTag("startup-${mode.name}"))
        }
        if (settings.mode in setOf(StartupMode.AUSTRALIAN, StartupMode.NEWS, StartupMode.SPORTS)) {
            Text("Choose a channel", style = MaterialTheme.typography.titleMedium)
            val isAutoSelected = settings.identity == null
            FocusButton((if (isAutoSelected) "✓ " else "") + "Automatic", { settings.chooseChannel(null) },
                Modifier.fillMaxWidth().semantics {
                    this.selected = isAutoSelected
                    this.role = Role.RadioButton
                    contentDescription = "Automatic channel selection${if (isAutoSelected) ", selected" else ""}"
                })
            val rows = StartupChannels.choices(model.guide?.channels.orEmpty(), settings.mode)
            if (rows.isEmpty()) Text("No channels available. Connect Australian services or refresh the guide.")
            if (settings.identity != null && rows.none { FavouriteIdentity.key(it) == settings.identity })
                Text("Your saved channel is currently unavailable. It will be kept for when it returns.")
            rows.forEach { channel ->
                val channelKey = FavouriteIdentity.key(channel)
                val isChannelSelected = settings.identity == channelKey
                FocusButton((if (isChannelSelected) "✓ " else "") + channel.name,
                    { settings.chooseChannel(channelKey) },
                    Modifier.fillMaxWidth().semantics {
                        this.selected = isChannelSelected
                        this.role = Role.RadioButton
                        contentDescription = "${channel.name}${if (isChannelSelected) ", selected" else ""}"
                    })
            }
        }
        Text("Unavailable channels are skipped if playback fails. You can always choose another channel from the guide.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
    }
}
