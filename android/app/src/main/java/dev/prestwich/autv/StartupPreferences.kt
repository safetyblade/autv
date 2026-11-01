package dev.prestwich.autv

import android.content.SharedPreferences
import androidx.compose.runtime.*
import dev.prestwich.autv.guide.StartupMode

/** Independent of viewing history; only explicit Settings actions change this choice. */
internal class StartupPreferences(private val preferences: SharedPreferences) {
    var mode by mutableStateOf(runCatching {
        StartupMode.valueOf(preferences.getString("startup-mode-v1", null).orEmpty())
    }.getOrDefault(StartupMode.LAST_WATCHED))
        private set
    var identity by mutableStateOf(preferences.getString("startup-channel-${mode.name}", null))
        private set
    fun choose(value: StartupMode) {
        mode = value
        identity = preferences.getString("startup-channel-${mode.name}", null)
        preferences.edit().putString("startup-mode-v1", mode.name).apply()
    }
    fun chooseChannel(value: String?) {
        identity = value
        preferences.edit().putString("startup-channel-${mode.name}", value).apply()
    }
}
