package dev.prestwich.autv

import android.content.Context
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.os.Bundle

/** Synthetic screenshot UI only. Set the configuration before launching this debug activity. */
class ProviderScreenshotActivity : ProviderActivationTestActivity() {
    companion object {
        @Volatile private var screenshotTv = false
        @Volatile private var screenshotFontScale = 1f

        fun configureForScreenshot(tv: Boolean, fontScale: Float) {
            require(fontScale > 0f)
            screenshotTv = tv
            screenshotFontScale = fontScale
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation=if(screenshotTv) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
    }

    override fun attachBaseContext(newBase: Context) {
        // applyOverrideConfiguration(onCreate) is too late after AppCompat resources are accessed.
        // A delta keeps window size/density responsive when the instrumentation changes wm.
        // Copying the complete process configuration freezes stale screenWidth/HeightDp.
        val config = Configuration().apply {
            uiMode = (newBase.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or
                (if (screenshotTv) Configuration.UI_MODE_TYPE_TELEVISION else Configuration.UI_MODE_TYPE_NORMAL)
            fontScale = screenshotFontScale
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
}
