package dev.prestwich.autv

import android.content.res.Configuration
import android.os.Bundle

/** Explicitly synthetic account UI, used only by screenshot/layout instrumentation. */
class ProviderScreenshotActivity : ProviderActivationTestActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyOverrideConfiguration(Configuration().apply {
            uiMode = if(intent.getBooleanExtra("tv", false)) Configuration.UI_MODE_TYPE_TELEVISION else Configuration.UI_MODE_TYPE_NORMAL
            fontScale = intent.getFloatExtra("fontScale",1f)
        })
        super.onCreate(savedInstanceState)
    }
}
