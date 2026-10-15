package dev.prestwich.autv.creator

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import kotlinx.coroutines.*

/** Explicit debug entry point; does not select, renumber or send anything to the FAST/Cast model. */
class CreatorPlaybackActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val availability = CreatorAvailability()
    private var channel = CreatorCatalogue.gdq
    private var result = Availability()
    private var request: Job? = null
    private var generation = 0
    private var youtubeKey = ""
    private var twitchClient = ""
    private var twitchToken = ""
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var embed: Button
    private lateinit var check: Button
    private var web: WebView? = null
    private var playerTimeout: Job? = null
    private val isTv get() = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        setContentView(root)
        root.addView(TextView(this).apply { text = "AUTV creator playback lab · DEBUG ONLY"; textSize = 20f })
        root.addView(TextView(this).apply { text = "Back closes the embed, then returns to AUTV. Cast remains with FAST. Status checks run only when requested." })
        val choices = LinearLayout(this)
        root.addView(choices)
        CreatorCatalogue.channels.forEach { item ->
            choices.addView(Button(this).apply {
                text = item.name
                setOnClickListener {
                    generation++; request?.cancel(); closeEmbed(); channel = item; result = Availability(); render()
                }
            })
        }
        status = TextView(this).apply { textSize = 16f }
        root.addView(status)
        val controls = LinearLayout(this)
        root.addView(HorizontalScrollView(this).apply { isFillViewport = true; addView(controls) })
        fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() }; controls.addView(this) }
        check = button("Check live", ::checkLive)
        embed = button("Play here", ::playEmbedded)
        button("Open official app", ::openOfficial)
        button("Return to AUTV") { finish() }
        root.addView(Button(this).apply { text = "Temporary API credentials"; setOnClickListener { credentials() } })
        render()
        check.requestFocus()
    }

    private fun render() {
        status.text = "${channel.name}: ${result.status}\n${result.message}" +
            if (channel.source is PlaybackSource.Twitch) "\nEmbedding deferred: requires a real hosted HTTPS parent page. Use the official app."
            else if (isTv) "\nTV embed is not validated. Use the official app."
            else ""
        embed.isEnabled = !isTv && channel.source is PlaybackSource.YouTube && result.status == LiveStatus.LIVE && result.videoId != null
        check.isEnabled = request?.isActive != true
    }

    private fun checkLive() {
        closeEmbed()
        val ticket = ++generation
        request?.cancel()
        check.isEnabled = false
        status.text = "${channel.name}: checking…"
        request = scope.launch {
            val source = channel.source
            val checked = withContext(Dispatchers.IO) {
                when (source) {
                    is PlaybackSource.Twitch -> availability.twitch(source.login, twitchClient, twitchToken)
                    is PlaybackSource.YouTube -> availability.youtube(source.channelId, youtubeKey)
                    is PlaybackSource.Hls -> Availability(message = "HLS remains in AUTV's existing Media3 player")
                }
            }
            if (ticket == generation) { result = checked; request = null; render() }
        }
    }

    private fun credentials() {
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 0, 24, 0) }
        fun field(label: String, value: String) = EditText(this).apply {
            hint = label; setText(value); isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSaveEnabled = false
            if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
            fields.addView(this)
        }
        val yt = field("YouTube Data API key", youtubeKey)
        val client = field("Twitch Client ID", twitchClient)
        val token = field("Twitch OAuth user token (no client secret)", twitchToken)
        AlertDialog.Builder(this).setTitle("Debug only · memory only").setMessage("Provide your own credentials to test the official metadata APIs. Nothing is saved or logged.")
            .setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Use") { _, _ ->
                generation++; request?.cancel(); request = null; closeEmbed()
                youtubeKey = yt.text.toString().trim(); twitchClient = client.text.toString().trim(); twitchToken = token.text.toString().trim()
                result = Availability(); render()
            }.show()
    }

    @Suppress("SetJavaScriptEnabled")
    private fun playEmbedded() {
        val id = result.videoId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: return
        if (isTv || channel.source !is PlaybackSource.YouTube || result.status != LiveStatus.LIVE) return
        closeEmbed()
        val density = resources.displayMetrics.density
        val occupied = (0 until root.childCount).sumOf { root.getChildAt(it).height }
        if ((root.width - root.paddingLeft - root.paddingRight) / density < 200 ||
            (root.height - root.paddingTop - root.paddingBottom - occupied) / density < 200) {
            result = result.copy(status = LiveStatus.UNAVAILABLE, message = "Not enough space for the official player; rotate the device or open YouTube")
            render(); return
        }
        try {
            val view = WebView(this)
            web = view
            view.settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = true
                allowFileAccess = false; allowContentAccess = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    // Player subframes remain untouched; top-level links go to the official app.
                    if (!request.isForMainFrame) return false
                    openOfficial(); return true
                }
            }
            view.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (web !== view) return true
                    when (message.message()) {
                        "AUTV:ready" -> {
                            playerTimeout?.cancel()
                            status.text = "${channel.name}: ${result.status}\nUse the YouTube player controls to begin playback"
                            return true
                        }
                        "AUTV:ended" -> result = Availability(LiveStatus.OFFLINE, "Broadcast ended; check live again")
                        "AUTV:error" -> result = Availability(LiveStatus.UNAVAILABLE, "YouTube player could not play this broadcast; use the official app")
                        "AUTV:blocked" -> { status.text = "Use the YouTube player controls to begin playback"; return true }
                        else -> return true
                    }
                    playerTimeout?.cancel(); render(); return true
                }
            }
            // YouTube's required-minimum-functionality explicitly specifies installed app ID
            // as the HTTPS Referer/baseURL for local HTML. This is not a Twitch parent workaround.
            val origin = "https://$packageName"
            val html = """
                <!doctype html><html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="referrer" content="strict-origin-when-cross-origin">
                <style>html,body,#player{width:100%;height:100%;margin:0;background:black}</style>
                </head><body><div id="player"></div><script src="https://www.youtube.com/iframe_api"></script>
                <script>function onYouTubeIframeAPIReady(){new YT.Player('player',{
                width:'100%',height:'100%',videoId:'$id',playerVars:{origin:'$origin',playsinline:1,autoplay:0,fs:0},
                events:{onReady:function(){console.log('AUTV:ready')},onError:function(){console.log('AUTV:error')},
                onAutoplayBlocked:function(){console.log('AUTV:blocked')},
                onStateChange:function(e){if(e.data===0)console.log('AUTV:ended')}}});}</script></body></html>
            """.trimIndent()
            view.loadDataWithBaseURL(origin, html, "text/html", "UTF-8", null)
            root.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
            view.requestFocus()
            playerTimeout = scope.launch {
                delay(20_000)
                if (web === view) {
                    closeEmbed()
                    result = result.copy(status = LiveStatus.UNAVAILABLE, message = "YouTube player did not become ready; retry or open the official app")
                    render(); check.requestFocus()
                }
            }
        } catch (_: Exception) {
            closeEmbed(); result = Availability(LiveStatus.UNAVAILABLE, "WebView unavailable; use the official app"); render()
        }
    }

    private fun openOfficial() {
        closeEmbed()
        val url = when (val source = channel.source) {
            is PlaybackSource.Twitch -> "https://www.twitch.tv/${source.login}"
            is PlaybackSource.YouTube -> result.videoId?.let { "https://www.youtube.com/watch?v=$it" }
                ?: "https://www.youtube.com/channel/${source.channelId}/live"
            is PlaybackSource.Hls -> return
        }
        val packages = if (channel.source is PlaybackSource.Twitch) listOf("tv.twitch.android.app")
            else if (isTv) listOf("com.google.android.youtube.tv", "com.google.android.youtube")
            else listOf("com.google.android.youtube", "com.google.android.youtube.tv")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://$packageName"))
        for (target in packages + listOf<String?>(null)) {
            val destination = Intent(intent).setPackage(target)
            if (target != null && channel.source is PlaybackSource.Twitch) {
                destination.data = Uri.parse("twitch://stream/${(channel.source as PlaybackSource.Twitch).login}")
            }
            try { startActivity(destination); return }
            catch (_: ActivityNotFoundException) { /* Try the next installed official handler or browser. */ }
            catch (_: SecurityException) { /* Handler cannot be opened on this device. */ }
        }
        result = result.copy(message = "No official app or browser can open this link on this device")
        render()
    }

    private fun closeEmbed() {
        playerTimeout?.cancel(); playerTimeout = null
        web?.let { root.removeView(it); it.stopLoading(); it.loadUrl("about:blank"); it.destroy() }
        web = null
    }
    @Deprecated("Debug activity uses the platform Back contract")
    override fun onBackPressed() {
        if (web != null) { closeEmbed(); check.requestFocus() } else super.onBackPressed()
    }
    override fun onStop() { closeEmbed(); super.onStop() }
    override fun onDestroy() { scope.cancel(); closeEmbed(); youtubeKey = ""; twitchToken = ""; super.onDestroy() }
}
