package dev.prestwich.autv.provider

import android.content.Context
import dev.prestwich.autv.data.provider.*

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
    configuration.abc?.let { registration ->
        configured("abc") { createRegisteredAbcProvider(context,registration,configuration.abcAnonymousLiveApproved) }
    }
}
