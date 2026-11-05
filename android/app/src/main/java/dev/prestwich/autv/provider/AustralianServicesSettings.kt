package dev.prestwich.autv.provider

import android.content.Intent
import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.prestwich.autv.*
import dev.prestwich.autv.R
import dev.prestwich.autv.data.provider.ProviderConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AustralianService(val id: String, val name: String, val description: String, @DrawableRes val icon: Int?)
private val services = listOf(
    AustralianService("9now", "9Now", "Live channels & special events", R.drawable.provider_nine),
    AustralianService("abc", "ABC iview", "Australia's national broadcaster", null),
    AustralianService("sbs", "SBS On Demand", "Stories from around the world", R.drawable.provider_sbs),
    AustralianService("seven", "7plus", "Seven Network", R.drawable.provider_seven),
    AustralianService("ten", "10", "Network 10", R.drawable.provider_ten),
)

/** App-native full-screen navigation; account/player lifecycles remain in the retained model. */
@Composable
internal fun AustralianServicesSettings(model: PlaybackModel, onClose: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val tv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    var selected by rememberSaveable { mutableStateOf<String?>(model.pendingActivationProvider) }
    var probeBusy by remember(selected) { mutableStateOf(false) }
    var probeResult by remember(selected) { mutableStateOf<NativeBroadcasterTrial.Result?>(null) }
    val probeScope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    LaunchedEffect(selected) { scroll.scrollTo(0); withFrameNanos { }; firstFocus.requestFocus() }
    Dialog(onDismissRequest = { if(selected!=null) selected=null else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        AuTvTheme {
            Surface(color = Ink, modifier = Modifier.fillMaxSize().testTag("australian-services-settings")) {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(if(tv) 24.dp else 16.dp)
                    .widthIn(max=1200.dp).fillMaxSize(), horizontalAlignment=Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().padding(bottom=12.dp), verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(selected!=null) FocusButton("‹ All services", {selected=null}, Modifier.focusRequester(firstFocus)
                            .semantics { contentDescription="Back to all services" }.testTag("providers-back"))
                        Column(Modifier.weight(1f)) {
                            Text("AUTV  /  SETTINGS", style=MaterialTheme.typography.labelMedium, color=Silver)
                            Text("Australian services",style=MaterialTheme.typography.titleLarge)
                        }
                        FocusButton("Close",onClose,Modifier.testTag("close-settings").semantics {contentDescription="Close settings"})
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=0.4f))
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top=12.dp)) {
                        val activationHeight=maxHeight
                        if(selected==null) {
                            val wide=maxWidth>=840.dp
                            val providers: @Composable ()->Unit = {
                                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text("Your networks. One guide.",style=MaterialTheme.typography.titleMedium)
                                    services.forEach { service ->
                                        ServiceCard(service,model,{selected=service.id},
                                            if(service.id=="9now") Modifier.focusRequester(firstFocus) else Modifier)
                                    }
                                }
                            }
                            if(wide) Row(Modifier.fillMaxSize().verticalScroll(scroll),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                                Column(Modifier.width(300.dp)) { StartupSettings(model) }
                                Box(Modifier.weight(1f)) { providers() }
                            } else Column(Modifier.fillMaxWidth().verticalScroll(scroll),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                StartupSettings(model);HorizontalDivider();providers()
                            }
                        } else {
                            val service=services.first {it.id==selected}
                            val account=model.providerAccounts[service.id]
                            val unavailable=model.providerUnavailableReason(service.id)
                            val connection=account?.connection ?: model.providerConnection
                            val activation=account?.activation ?: if(service.id=="9now") model.providerActivation else null
                            val disconnect: ()->Unit=account?.let {it::disconnect} ?: model::disconnectNineNow
                            if(activation!=null) {
                                val destination=when(service.id) {"seven"->ActivationDestination.SEVEN;"sbs"->ActivationDestination.SBS;"abc"->ActivationDestination.ABC;"ten"->ActivationDestination.TEN;else->ActivationDestination.NINE_NOW}
                                Box(Modifier.fillMaxSize().verticalScroll(scroll),contentAlignment=Alignment.Center) {
                                    ProviderActivationScreen(service.name,activation,destination,
                                        {address->runCatching {uriHandler.openUri(address)};Unit},disconnect,viewportHeight=activationHeight)
                                }
                            } else Column(Modifier.widthIn(max=900.dp).fillMaxWidth().align(Alignment.TopCenter).verticalScroll(scroll),
                                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    ServiceIcon(service)
                                    Column { Text(service.name,style=MaterialTheme.typography.headlineSmall)
                                        Text(service.description,color=Muted,style=MaterialTheme.typography.bodyMedium) }
                                }
                                if(service.id in setOf("abc", "ten")) {
                                    val liveChannels = AustralianLiveChannels.approved.filter { item ->
                                        item.provider == if(service.id == "abc") "ABC iview" else "10"
                                    }
                                    Text("Live TV available", style=MaterialTheme.typography.titleMedium,
                                        modifier=Modifier.testTag("${service.id}-connection-state"))
                                    Text("${liveChannels.size} live channels · No sign-in required",
                                        color=Silver, style=MaterialTheme.typography.bodyLarge,
                                        modifier=Modifier.testTag("${service.id}-live-channel-count"))
                                    Text("Included in your Australian TV guide:",
                                        style=MaterialTheme.typography.bodyMedium, color=Muted)
                                    Text(liveChannels.joinToString("  ·  ") { it.name },
                                        style=MaterialTheme.typography.bodyMedium)
                                    FocusButton("Open Australian TV guide", {
                                        model.openGuide()
                                        onClose()
                                    }, Modifier.testTag("${service.id}-open-guide"))
                                    Text("These free live streams use an independent source. They do not need your broadcaster account.",
                                        style=MaterialTheme.typography.bodySmall, color=Muted)

                                    if(service.id == "abc" && account != null) {
                                        var accountExpanded by rememberSaveable(service.id) { mutableStateOf(false) }
                                        HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=0.4f))
                                        FocusButton(if(accountExpanded) "Hide optional ABC account options"
                                            else "ABC account options (optional)", {
                                            accountExpanded = !accountExpanded
                                        }, Modifier.testTag("abc-optional-account-options"))
                                        if(accountExpanded) {
                                            Text(if(account.connection == ProviderConnection.CONNECTED)
                                                "ABC account linked · not used for live TV"
                                                else "ABC account not linked · live TV still works",
                                                color=Silver,style=MaterialTheme.typography.bodyMedium)
                                            Text("The existing ABC device-code connection is retained for possible future iview features.",
                                                style=MaterialTheme.typography.bodySmall,color=Muted)
                                            account.status?.let { Text(it,color=Muted) }
                                            account.error?.let {
                                                Text(it,color=MaterialTheme.colorScheme.error,
                                                    modifier=Modifier.testTag("abc-error"))
                                            }
                                            if(account.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                            else if(account.connection == ProviderConnection.CONNECTED) {
                                                FocusButton("Sign out of ABC iview",account::disconnect,
                                                    Modifier.testTag("abc-disconnect"))
                                            } else if(account.accountLinkingEnabled) {
                                                FocusButton("Link ABC account",account::connect,
                                                    Modifier.testTag("abc-connect"))
                                            }
                                        }
                                    }
                                } else if(unavailable!=null) {
                                    Text("Configuration required",style=MaterialTheme.typography.titleMedium)
                                    Text(unavailable,color=Muted,modifier=Modifier.testTag("${service.id}-unavailable"))
                                } else {
                                    val busy=account?.busy ?: model.providerBusy
                                    val count=account?.channels?.size ?: model.providerChannelCount
                                    val status=account?.status ?: if(service.id=="9now") model.providerAuthStatus else null
                                    val error=account?.error ?: if(service.id=="9now") model.providerError else null
                                    val canBrowse=account?.canBrowse ?: (connection==ProviderConnection.CONNECTED)
                                    Text(serviceState(service.id,connection,account?.anonymousLiveApproved==true),color=Silver,modifier=Modifier.testTag("${service.id}-connection-state"))
                                    if (service.id=="abc" && model.experimentalAbcLink) {
                                        Text("Experimental ABC account link. AUTV can request a genuine ABC code, but approval and native media authorisation must be verified on this TV.",color=Muted,style=MaterialTheme.typography.bodySmall)
                                        if (BuildConfig.DEBUG) {
                                            FocusButton(if(probeBusy) "Checking ABC…" else "Check ABC live catalogue",{
                                                if(!probeBusy) {
                                                    probeBusy=true
                                                    probeScope.launch {
                                                        try {
                                                            val result=withContext(Dispatchers.IO) { NativeBroadcasterTrial.inspect("abc") }
                                                            if(selected=="abc") probeResult=result
                                                        } finally { if(selected=="abc") probeBusy=false }
                                                    }
                                                }
                                            },Modifier.testTag("abc-native-probe"),!probeBusy)
                                            probeResult?.let { result ->
                                                Text("${result.stage}: ${result.summary}",modifier=Modifier.testTag("abc-native-probe-result"))
                                                if(result.channels.isNotEmpty()) Text(result.channels.joinToString(" • "),style=MaterialTheme.typography.bodySmall,color=Muted)
                                            }
                                        }
                                    }
                                    Text(if(canBrowse) "$count live channels in your guide." else "Connect your ${service.name} account to add its live channels.")
                                    if(service.id in setOf("abc","ten") && canBrowse) Text("Account linking and channel discovery do not verify media authorisation or playback. Select a channel in the guide to test it.",style=MaterialTheme.typography.bodySmall,color=Muted)
                                    status?.let {Text(it,color=Muted,modifier=Modifier.testTag("${service.id}-auth-status"))}
                                    error?.let {Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite}.testTag("${service.id}-error"))}
                                    if(!busy && connection!=ProviderConnection.CONNECTED && account?.accountLinkingEnabled!=false)
                                        FocusButton("Connect ${service.name}",account?.let {it::connect} ?: model::connectNineNow,Modifier.testTag("${service.id}-connect"))
                                    if(canBrowse) FocusButton(if(busy) "Refreshing…" else "Refresh channels",account?.let {it::refresh} ?: model::refreshProviderChannels,Modifier.testTag("${service.id}-refresh"),!busy)
                                    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().semantics {contentDescription="Loading ${service.name} channels"})
                                    if(busy || connection in setOf(ProviderConnection.CONNECTED,ProviderConnection.CONNECTING))
                                        FocusButton(if(connection==ProviderConnection.CONNECTED) "Sign out of ${service.name}" else "Cancel sign-in",disconnect,Modifier.testTag("${service.id}-disconnect"))
                                    Text("Australia only. Native playback on this device. Your connection is saved securely; sign in on the provider's website.",style=MaterialTheme.typography.bodySmall,color=Muted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceCard(service: AustralianService,model: PlaybackModel,onClick: ()->Unit,modifier: Modifier) {
    var focused by remember {mutableStateOf(false)}
    val account=model.providerAccounts[service.id]
    val state=when {
        service.id in setOf("abc","ten") -> "${AustralianLiveChannels.count(service.id)} live channels · No sign-in required"
        service.id=="9now" -> connectionLabel(model.providerConnection)
        account!=null -> serviceState(service.id,account.connection,account.anonymousLiveApproved)
        else -> "Configuration required"
    }
    OutlinedCard(onClick=onClick,shape=RoundedCornerShape(12.dp),
        colors=CardDefaults.outlinedCardColors(containerColor=if(focused) Silver.copy(alpha=0.22f) else Glass),
        modifier=modifier.fillMaxWidth().onFocusChanged {focused=it.isFocused}
            .border(if(focused) 3.dp else 1.dp,if(focused) WarmWhite else Color.White.copy(alpha=0.1f),RoundedCornerShape(12.dp))
            .semantics(mergeDescendants=true) {contentDescription="${service.name}, ${service.description} · $state"}.testTag("provider-${service.id}")) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            ServiceIcon(service)
            Column(Modifier.weight(1f)) {Text(service.name,style=MaterialTheme.typography.titleMedium);Text(state,style=MaterialTheme.typography.labelMedium,color=Muted)}
            Text("›",color=Silver,modifier=Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun ServiceIcon(service: AustralianService) {
    Surface(shape=RoundedCornerShape(12.dp),color=Color.White,modifier=Modifier.size(48.dp)) {
        if(service.icon!=null) Image(painterResource(service.icon),contentDescription=null,modifier=Modifier.fillMaxSize())
        else Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Text("ABC",color=Ink,style=MaterialTheme.typography.titleSmall)
            Text("iview",color=Ink,style=MaterialTheme.typography.labelSmall)
        }
    }
}
private fun serviceState(id: String,connection: ProviderConnection,anonymous: Boolean)=when {
    anonymous && connection!=ProviderConnection.CONNECTED -> "Live TV without an account"
    id in setOf("abc","ten") && connection==ProviderConnection.CONNECTED -> "Account linked · playback not verified"
    else -> connectionLabel(connection)
}
private fun connectionLabel(connection: ProviderConnection): String=when(connection) {
    ProviderConnection.NOT_CONNECTED->"Not connected"
    ProviderConnection.CONNECTING->"Waiting for sign-in"
    ProviderConnection.CONNECTED->"Connected"
    ProviderConnection.SESSION_EXPIRED->"Reconnect to continue"
    ProviderConnection.UNSUPPORTED->"Unavailable in this build"
}
