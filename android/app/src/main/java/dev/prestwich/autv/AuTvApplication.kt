package dev.prestwich.autv

import android.app.Application
import dev.prestwich.autv.provider.AustralianProviderConfiguration

/** Approved provider distributions override configuration; no third-party secrets ship by default. */
open class AuTvApplication : Application() {
    open val australianProviders: AustralianProviderConfiguration by lazy { AustralianProviderConfiguration() }
}
