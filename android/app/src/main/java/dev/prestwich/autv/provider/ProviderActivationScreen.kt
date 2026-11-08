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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import dev.prestwich.autv.FocusButton
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
    viewportHeight: Dp = Dp.Unspecified,
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
    val qrSize = if(viewportHeight == Dp.Unspecified) 192.dp else (viewportHeight * 0.32f).coerceIn(128.dp,224.dp)
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().testTag("provider-activation-screen")) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Connect $providerName",style=MaterialTheme.typography.titleLarge,textAlign=TextAlign.Center)
            if(remaining>0 && bitmap!=null) Image(bitmap,
                contentDescription="Scan with your phone camera to open $providerName's official activation website. You can also use the website and code shown here.",
                filterQuality=FilterQuality.None,
                modifier=Modifier.size(qrSize).background(Color.White).testTag("activation-qr"))
            Text("Scan with your phone camera",style=MaterialTheme.typography.titleMedium,textAlign=TextAlign.Center)
            Text("Or open this official website and enter the code:",style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center)
            Text(destination.address,style=MaterialTheme.typography.bodyLarge,textAlign=TextAlign.Center,modifier=Modifier.testTag("activation-address"))
            if(remaining>0) {
                val codeDescription="Activation code: ${challenge.userCode.map {it.toString()}.joinToString(" ")}"
                Text(challenge.userCode,style=MaterialTheme.typography.headlineLarge,textAlign=TextAlign.Center,
                    modifier=Modifier.semantics {contentDescription=codeDescription}.testTag("activation-code"))
                val expiryDescription = "Expires in ${remaining / 60} minutes ${(remaining % 60)} seconds"
                Text("Expires in ${remaining/60}:${(remaining%60).toString().padStart(2,'0')}",style=MaterialTheme.typography.bodySmall,
                    modifier=Modifier.semantics { contentDescription = expiryDescription }.testTag("activation-expiry"))
            }
            if(remaining==0L) Text("This code has expired. Cancel and request a new code.",
                color=MaterialTheme.colorScheme.error,textAlign=TextAlign.Center,
                modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite}.testTag("activation-expired"))
            else if(payload==null) Text("The activation address could not be verified. Cancel and try again.",color=MaterialTheme.colorScheme.error)
            else Text("Sign in on $providerName's website. Return to AUTV after approval.",
                style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.CenterHorizontally),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                FocusButton("Open official sign-in",{payload?.let(onOpenWebsite)},
                    Modifier.focusProperties {down=cancelFocus;next=cancelFocus}.focusRequester(websiteFocus).testTag("activation-open-website"),
                    remaining>0 && payload!=null)
                FocusButton("Cancel sign-in",onCancel,
                    Modifier.focusProperties {if(remaining>0 && payload!=null) up=websiteFocus}.focusRequester(cancelFocus).testTag("activation-cancel"))
            }
            Text("Account linking does not verify channel playback.",style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center)
        }
    }
}
