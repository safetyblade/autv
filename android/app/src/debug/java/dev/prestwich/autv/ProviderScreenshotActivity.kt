package dev.prestwich.autv

import android.content.Context
import android.content.res.Configuration

/** Only screenshot instrumentation writes these before launching the fixture activity. */
object ScreenshotDeviceConfiguration {
    @Volatile var television = false
    @Volatile var fontScale = 1f
}

/** Synthetic account UI. Configure resources before AppCompat or Android reads them. */
class ProviderScreenshotActivity : ProviderActivationTestActivity() {
    override fun attachBaseContext(newBase: Context) {
        val configuration=Configuration(newBase.resources.configuration).apply {
            uiMode=(uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or
                if(ScreenshotDeviceConfiguration.television) Configuration.UI_MODE_TYPE_TELEVISION else Configuration.UI_MODE_TYPE_NORMAL
            fontScale=ScreenshotDeviceConfiguration.fontScale
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }
}
