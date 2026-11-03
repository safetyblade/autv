package dev.prestwich.autv.provider

import android.content.Context
import dev.prestwich.autv.data.provider.AbcClientRegistration
import dev.prestwich.autv.data.provider.TenClientRegistration

/** Approved distribution code supplies callbacks to its signing service, never embedded secrets.
 * Sources must have a public no-argument constructor and perform no network IO during construction.
 */
fun interface AbcRegistrationSource {
    fun configuration(context: Context): AbcProviderConfiguration
}

fun interface TenRegistrationSource {
    fun configuration(context: Context): TenProviderConfiguration
}

class AbcProviderConfiguration(val registration: AbcClientRegistration, val anonymousLiveApproved: Boolean = false) {
    init { require(registration.accountLinkingEnabled || anonymousLiveApproved) }
    override fun toString() = "AbcProviderConfiguration(redacted)"
}

class TenProviderConfiguration(val registration: TenClientRegistration, val proofChannelKey: String, val daiNetworkCode: String? = null) {
    init { require(proofChannelKey.isNotBlank()) }
    override fun toString() = "TenProviderConfiguration(redacted)"
}

/** Independent loading: a broken or absent source must not suppress another broadcaster. */
internal fun loadAustralianProviderConfiguration(
    abcSource: () -> AbcProviderConfiguration?,
    tenSource: () -> TenProviderConfiguration?,
): AustralianProviderConfiguration {
    val failures = mutableSetOf<String>()
    fun <T> load(id: String, source: () -> T?): T? = try { source() }
    catch (_: Exception) { failures.add(id); null }
    catch (_: LinkageError) { failures.add(id); null }
    val abc = load("abc", abcSource)
    val ten = load("ten", tenSource)
    return AustralianProviderConfiguration(
        abc = abc?.registration, abcAnonymousLiveApproved = abc?.anonymousLiveApproved ?: false,
        ten = ten?.registration, tenProofChannelKey = ten?.proofChannelKey, tenDaiNetworkCode = ten?.daiNetworkCode,
        configurationFailures = failures,
    )
}

internal inline fun <reified T> registrationSource(className: String): T? =
    if (className.isBlank()) null else Class.forName(className).getDeclaredConstructor().newInstance() as T
