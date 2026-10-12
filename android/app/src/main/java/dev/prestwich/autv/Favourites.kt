package dev.prestwich.autv

import android.content.Context
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.compose.runtime.*
import dev.prestwich.autv.data.Channel
import dev.prestwich.autv.guide.FavouriteIdentity
import dev.prestwich.autv.guide.RemoteOkPress
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Only canonical membership is stored; the published guide remains the sole catalogue. */
internal class Favourites(context: Context) {
    private val preferences = context.getSharedPreferences("favourites-v1", Context.MODE_PRIVATE)
    var keys by mutableStateOf(preferences.getStringSet("channels", emptySet()).orEmpty().toSet())
        private set
    fun toggle(channel: Channel): Boolean? {
        val key = FavouriteIdentity.key(channel) ?: return null
        val added = key !in keys
        keys = if (added) keys + key else keys - key
        preferences.edit().putStringSet("channels", keys.toSet()).apply()
        return added
    }
}

internal fun favouriteConfirmation(context: Context, added: Boolean?) {
    Toast.makeText(context, when (added) {
        true -> "Added to Favourites"
        false -> "Removed from Favourites"
        null -> "This channel has no stable identity yet"
    }, Toast.LENGTH_SHORT).show()
}

/** Deferred short OK is essential: an initial down cannot tune before we know it is short. */
@Composable
internal fun rememberRemoteOk(onShort: () -> Unit, onLong: () -> Unit, active: Boolean = true): (KeyEvent) -> Boolean {
    val short by rememberUpdatedState(onShort)
    val long by rememberUpdatedState(onLong)
    val press = remember { RemoteOkPress() }
    val scope = rememberCoroutineScope()
    var timer by remember { mutableStateOf<Job?>(null) }
    val timeout = ViewConfiguration.getLongPressTimeout().toLong()
    DisposableEffect(active) { onDispose { timer?.cancel(); press.cancel() } }
    fun emit(action: RemoteOkPress.Action) {
        when (action) { RemoteOkPress.Action.SHORT -> short(); RemoteOkPress.Action.LONG -> long(); else -> Unit }
    }
    return { key ->
        if (!active || key.keyCode !in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)) {
            if (key.action == KeyEvent.ACTION_DOWN) { timer?.cancel(); press.cancel() }
            false
        }
        else {
            when (key.action) {
                KeyEvent.ACTION_DOWN -> {
                    emit(press.down(key.eventTime, key.repeatCount > 0, key.isLongPress, timeout))
                    if (key.repeatCount == 0) {
                        timer?.cancel()
                        timer = scope.launch { delay(timeout); emit(press.hold()) }
                    }
                }
                KeyEvent.ACTION_UP -> { timer?.cancel(); emit(press.up(key.eventTime, timeout, key.isCanceled)) }
            }
            true
        }
    }
}
