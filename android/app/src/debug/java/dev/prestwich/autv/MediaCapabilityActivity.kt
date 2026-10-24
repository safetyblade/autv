package dev.prestwich.autv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.Log
import androidx.media3.ui.PlayerView

/** Isolated debug-only public sample UI; retains one player/session across recreation. */
class MediaCapabilityActivity : AppCompatActivity() {
    lateinit var session: MediaLabSession; private set
    val holder get()=session.holder
    val video get()=session.video
    val audio get()=session.audio
    val drmKeys get()=session.drmKeys
    val errorCode get()=session.errorCode
    val imaError get()=session.imaError
    val sessionFailure get()=session.sessionFailure
    val adStarted get()=session.adStarted
    val adEnded get()=session.adEnded
    val completedAds get()=session.completedAds
    val adMediaObserved get()=session.adMediaObserved
    val expired get()=session.expired
    val fallbackVideo get()=session.fallbackVideo
    val fallbackAudio get()=session.fallbackAudio
    private lateinit var status: TextView
    private lateinit var videoArea: FrameLayout
    private lateinit var videoView: PlayerView
    private val handler=Handler(Looper.getMainLooper())
    private val ticker=object : Runnable { override fun run() { session.refresh();handler.postDelayed(this,500) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.setLogLevel(Log.LOG_LEVEL_OFF)
        session=ViewModelProvider(this)[MediaLabSession::class.java]
        android.util.Log.i("AUTV_MEDIA_QA","environment sdk=${android.os.Build.VERSION.SDK_INT} webview=${if(android.os.Build.VERSION.SDK_INT>=26) android.webkit.WebView.getCurrentWebViewPackage()?.versionName else "pre26"}")
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(0xff10141a.toInt()) }
        // Reserve visible video space even when the status text grows. All controls scroll separately.
        videoArea=FrameLayout(this)
        root.addView(videoArea,LinearLayout.LayoutParams(-1,0,1f))
        val scroll=ScrollView(this).apply { isFillViewport=true }
        val controls=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8)) }
        scroll.addView(controls)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        for(pair in MediaLabSession.SAMPLES.chunked(2)) {
            val row=LinearLayout(this)
            for((name,label) in pair) row.addView(Button(this).apply {
                text=label;isAllCaps=false;minHeight=dp(48);isFocusable=true
                tag=name;setOnClickListener { start(name) }
            },LinearLayout.LayoutParams(0,-2,1f))
            controls.addView(row)
        }
        controls.addView(Button(this).apply {
            text="Copy QA summary";isAllCaps=false;minHeight=dp(48);tag="copy-qa"
            setOnClickListener {
                val clipboard=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AUTV Media Lab",session.summary()))
                Toast.makeText(this@MediaCapabilityActivity,"QA summary copied",Toast.LENGTH_SHORT).show()
            }
        })
        status=TextView(this).apply { textSize=15f;setTextColor(0xffeeeeee.toInt());setTextIsSelectable(true) }
        controls.addView(status)
        setContentView(root);rebindVideoView()
        session.changed={ status.text=session.summary() }
        status.text=session.summary()
        // Only a reviewed sample ID survives process death, never a DAI URL/session/token.
        if(session.current=="none") savedInstanceState?.getString("sample")?.takeIf { key -> MediaLabSession.SAMPLES.any { it.first==key } }?.let(::start)
    }
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    fun rebindVideoView() {
        if(::videoView.isInitialized) { holder.detachPlayerView(videoView);videoView.player=null;videoArea.removeView(videoView) }
        videoView=PlayerView(this).apply { player=holder.player;useController=true;keepScreenOn=true;tag="media-video" }
        videoArea.addView(videoView,FrameLayout.LayoutParams(-1,-1));holder.attachPlayerView(videoView)
    }
    fun start(sample: String)=session.start(sample)
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("sample",session.current);super.onSaveInstanceState(outState) }
    override fun onPause() { handler.removeCallbacks(ticker);session.resumeAfterPause=holder.player.playWhenReady;holder.player.pause();videoView.onPause();super.onPause() }
    override fun onResume() { super.onResume();videoView.onResume();if(session.resumeAfterPause && holder.player.mediaItemCount>0) holder.player.play();handler.post(ticker) }
    override fun onDestroy() {
        handler.removeCallbacks(ticker);session.changed=null
        holder.detachPlayerView(videoView);videoView.player=null
        super.onDestroy() // ViewModel releases only when actually finished, retaining DAI on rotation.
    }
}
