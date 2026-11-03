package dev.prestwich.autv

import android.content.Context
import android.content.res.Configuration

/** Explicitly synthetic account UI, used only by screenshot/layout instrumentation. */
class ProviderScreenshotActivity : ProviderActivationTestActivity() {
    override fun attachBaseContext(base: Context) {
        val config=Configuration(base.resources.configuration).apply {
            uiMode=(uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or
                if(television) Configuration.UI_MODE_TYPE_TELEVISION else Configuration.UI_MODE_TYPE_NORMAL
            fontScale=1f
        }
        // onCreate is too late: AppCompat/instrumentation may already access resources.
        super.attachBaseContext(base.createConfigurationContext(config))
    }
    companion object { @Volatile var television=false }
}
