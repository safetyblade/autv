package dev.prestwich.autv.provider

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.prestwich.autv.data.provider.DeviceActivation
import kotlinx.coroutines.delay

/** Shared UI only: the retained provider model owns persistence, polling and credentials. */
@Composable
internal fun ProviderActivationScreen(
    providerName: String,
    challenge: DeviceActivation,
    destination: ActivationDestination,
    onOpenWebsite: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val websiteFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    var now by remember(challenge) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(challenge) {
        while (now < challenge.expiresAt) { delay(1000); now = System.currentTimeMillis() }
    }
    val remaining = ((challenge.expiresAt - now + 999) / 1000).coerceAtLeast(0)
    val payload = remember(challenge, destination) { ActivationQr.payload(challenge, destination) }
    val bitmap = remember(challenge, destination) {
        ActivationQr.encode(challenge, destination)?.let { matrix ->
            val pixels = IntArray(matrix.width * matrix.height) { index ->
                if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
            Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().testTag("provider-activation-screen")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Connect $providerName", style = MaterialTheme.typography.titleLarge)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 520.dp
                val qr: @Composable () -> Unit = {
                    if (remaining > 0 && bitmap != null) {
                        Image(bitmap, contentDescription = "Scan with your phone camera to open $providerName's official activation website. You can also use the website and code shown here.",
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.size(224.dp).background(Color.White).testTag("activation-qr"))
                    }
                }
                val instructions: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Scan with your phone camera", style = MaterialTheme.typography.titleMedium)
                        Text("Or open this official website and enter the code:")
                        Text(destination.address, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("activation-address"))
                        if (remaining > 0) {
                            Text(challenge.userCode, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("activation-code"))
                            Text("Expires in ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("activation-expiry"))
                        }
                    }
                }
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    qr()
                    Box(Modifier.weight(1f)) { instructions() }
                } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    qr()
                    instructions()
                }
            }
            if (remaining == 0L) Text("This code has expired. Cancel and request a new code.",
                color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("activation-expired"))
            else if (payload == null) Text("The activation address could not be verified. Cancel and try again.", color = MaterialTheme.colorScheme.error)
            else Text("Sign in on $providerName's website. AUTV will connect automatically after approval. If using this phone, return to AUTV after submitting the code.")
            Text("Connecting your account does not guarantee that every channel can play.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { payload?.let(onOpenWebsite) }, enabled = remaining > 0 && payload != null,
                modifier = Modifier.focusProperties { canFocus = true; down = cancelFocus; next = cancelFocus }.focusRequester(websiteFocus).testTag("activation-open-website")) { Text("Open official sign-in") }
            TextButton(onClick = onCancel, modifier = Modifier.focusProperties { canFocus = true; if (remaining > 0 && payload != null) up = websiteFocus }.focusRequester(cancelFocus).testTag("activation-cancel")) { Text("Cancel sign-in") }
        }
    }
}
