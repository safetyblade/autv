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
) {
    init {
        require((ten==null)==(tenProofChannelKey==null))
        require(tenProofChannelKey==null || tenProofChannelKey.isNotBlank())
        require(abc==null || abc.accountLinkingEnabled || abcAnonymousLiveApproved)
    }
    override fun toString()="AustralianProviderConfiguration(redacted)"
}

/** Production composition: concrete transports + their existing encrypted account factories. */
internal fun configuredAustralianAdapters(context: Context, configuration: AustralianProviderConfiguration): Map<String,ProviderAdapter> = buildMap {
    put("sbs",createSbsProvider(context))
    configuration.ten?.let { registration ->
        put("ten",createRegisteredTenProvider(context,registration,configuration.tenProofChannelKey!!,configuration.tenDaiNetworkCode))
    }
    configuration.abc?.let { registration ->
        put("abc",createRegisteredAbcProvider(context,registration,configuration.abcAnonymousLiveApproved))
    }
}
