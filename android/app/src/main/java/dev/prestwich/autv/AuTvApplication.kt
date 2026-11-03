package dev.prestwich.autv

import android.app.Application
import dev.prestwich.autv.provider.*

/** Approved provider distributions override configuration; no third-party secrets ship by default. */
open class AuTvApplication : Application() {
    open val australianProviders: AustralianProviderConfiguration by lazy {
        loadAustralianProviderConfiguration(
            abcSource = { registrationSource<AbcRegistrationSource>(BuildConfig.ABC_REGISTRATION_SOURCE)?.configuration(this) },
            tenSource = { registrationSource<TenRegistrationSource>(BuildConfig.TEN_REGISTRATION_SOURCE)?.configuration(this) },
        )
    }
}
