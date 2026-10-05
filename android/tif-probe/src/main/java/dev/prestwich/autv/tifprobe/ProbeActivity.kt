package dev.prestwich.autv.tifprobe

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvView
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.*
import java.util.concurrent.Executors

/** Explicit diagnostic/setup UI; nothing is registered in the primary AU TV manifest. */
class ProbeActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var diagnostics: TextView
    private lateinit var video: TvView
    private var sample: Uri? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        diagnostics = TextView(this).apply { textSize = 16f }
        layout.addView(diagnostics)
        fun button(label: String, action: () -> Unit) { layout.addView(Button(this).apply { text = label; setOnClickListener { action() } }) }
        button("Refresh capability report") { refresh() }
        button("Publish 3 owned test channels") {
            diagnostics.text = "Loading existing AU TV catalogue…"
            executor.execute {
                val result = runCatching { ProbeCatalogue.publish(this) }
                runOnUiThread { result.fold(onSuccess = { sample = it.firstOrNull(); refresh("Published ${it.size} test channels. Enable these in system Live TV setup.") },
                    onFailure = { refresh("Publish failed: ${it.javaClass.simpleName}: ${it.message}") }) }
            }
        }
        button("Test sample in embedded system TvView") {
            val input = runCatching { ProbeCatalogue.inputId(this) }.getOrNull()
            if (input == null || sample == null) refresh("Publish the sample channels first.") else video.tune(input, sample)
        }
        button("Open system TV/input experience") {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, sample ?: android.media.tv.TvContract.Channels.CONTENT_URI)) }
                .onFailure { refresh("No compatible system TV activity: ${it.javaClass.simpleName}") }
        }
        button("Finish input setup") { setResult(RESULT_OK); finish() }
        video = TvView(this).apply {
            setCallback(object : TvView.TvInputCallback() {
                override fun onVideoAvailable(inputId: String) { refresh("Embedded TvView: VIDEO AVAILABLE") }
                override fun onVideoUnavailable(inputId: String, reason: Int) { refresh("Embedded TvView: video unavailable ($reason)") }
                override fun onConnectionFailed(inputId: String) { refresh("Embedded TvView: system binding failed") }
            })
        }
        layout.addView(video, LinearLayout.LayoutParams(-1, 240))
        setContentView(ScrollView(this).apply { addView(layout) })
        refresh()
    }
    private fun refresh(message: String = "") {
        val input = runCatching { ProbeCatalogue.inputId(this) }.fold(onSuccess = { it ?: "NOT REGISTERED" }, onFailure = { "${it.javaClass.simpleName}: ${it.message}" })
        val prefs = getSharedPreferences("probe", 0)
        diagnostics.text = "AU TV Input Probe — isolated experiment\nAPI ${Build.VERSION.SDK_INT} · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Leanback: ${packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)} · Live TV: ${packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_TV)}\n" +
            "PiP feature advertised: ${packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)}\nInput: $input\n" +
            listOf("session", "tune", "channel", "video", "error").joinToString("\n") { "$it: ${prefs.getString(it, "Not observed")}" } +
            "\n$message\nPhysical TV-button routing is not established by this probe."
    }
    override fun onStop() { video.reset(); super.onStop() }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
