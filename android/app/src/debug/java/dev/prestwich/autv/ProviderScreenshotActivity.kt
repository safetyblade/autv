package dev.prestwich.autv

import android.content.Context
import android.content.res.Configuration

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

    override fun attachBaseContext(newBase: Context) {
        // applyOverrideConfiguration(onCreate) is too late after AppCompat resources are accessed.
        val config = Configuration(newBase.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or
                (if (screenshotTv) Configuration.UI_MODE_TYPE_TELEVISION else Configuration.UI_MODE_TYPE_NORMAL)
            fontScale = screenshotFontScale
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
}
