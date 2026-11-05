package dev.prestwich.autv.provider

import android.content.Context
import dev.prestwich.autv.data.provider.*
import dev.prestwich.autv.BuildConfig

/** Supplied by an approved AUTV build, never by passwords or public sample credentials. */
class AustralianProviderConfiguration(
    val ten: TenClientRegistration? = null,
    val tenProofChannelKey: String? = null,
    val tenDaiNetworkCode: String? = null,
    val abc: AbcClientRegistration? = null,
    val abcAnonymousLiveApproved: Boolean = false,
    configurationFailures: Set<String> = emptySet(),
) {
    val configurationFailures = configurationFailures.toSet()
    init {
        require((ten==null)==(tenProofChannelKey==null))
        require(tenProofChannelKey==null || tenProofChannelKey.isNotBlank())
        require(abc==null || abc.accountLinkingEnabled || abcAnonymousLiveApproved)
    }
    override fun toString()="AustralianProviderConfiguration(redacted)"
}

/** Production composition: concrete transports + their existing encrypted account factories. */
internal fun configuredAustralianAdapters(context: Context, configuration: AustralianProviderConfiguration,
    onConfigurationFailure: (String) -> Unit = {}): Map<String,ProviderAdapter> = buildMap {
    put("sbs",createSbsProvider(context))
    put("seven",createSevenProvider(context))
    fun configured(id: String, factory: () -> ProviderAdapter) {
        try { put(id, factory()) }
        catch (_: Exception) { onConfigurationFailure(id) }
        catch (_: LinkageError) { onConfigurationFailure(id) }
    }
    configuration.ten?.let { registration ->
        configured("ten") { createRegisteredTenProvider(context,registration,configuration.tenProofChannelKey!!,configuration.tenDaiNetworkCode) }
    }
    val abcRegistration = configuration.abc
    if (abcRegistration != null) {
        configured("abc") { createRegisteredAbcProvider(context,abcRegistration,configuration.abcAnonymousLiveApproved) }
    } else if (BuildConfig.DEBUG && "abc" !in configuration.configurationFailures) {
        // Genuine AUTV-identified code issuance was seen on 5 November. No borrowed client key.
        // Do not give the experimental session access to authorised distribution credentials.
        configured("abc") { createAbcProvider(context,AbcExperimentalLinkAccess(),
            "debug-link-only",experimental=true) }
    }
}
