package dev.prestwich.autv.data.provider

import java.net.URI
import java.util.Locale

/** Ephemeral provider-issued values. Never serialize into guide, favourites, Cast or logs. */
class ProviderWidevine(val licenceUri: String, headers: Map<String, String> = emptyMap(), val multiSession: Boolean = false) {
    val headers = headers.toMap()
    init { ProviderOriginHeaders.origin(licenceUri); ProviderOriginHeaders.validateHeaders(this.headers) }
    override fun toString() = "ProviderWidevine(redacted)"
}
class ProviderImaLive(val assetKey: String, val networkCode: String? = null,
    val authToken: String? = null, val apiKey: String? = null, adParameters: Map<String, String> = emptyMap()) {
    val adParameters = adParameters.toMap()
    init {
        require(assetKey.isNotBlank() && assetKey.length <= 512 && assetKey.none { it.isISOControl() })
        require(listOfNotNull(networkCode, authToken, apiKey).all { it.length <= 16384 && it.none { c -> c.isISOControl() } })
        require(this.adParameters.size <= 64 && this.adParameters.all { (k,v) -> k.length in 1..128 && v.length <= 4096 && (k+v).none { it.isISOControl() } })
    }
    override fun toString() = "ProviderImaLive(redacted)"
}
class ProviderMediaOptions(val widevine: ProviderWidevine? = null, val imaLive: ProviderImaLive? = null,
    val expiresAt: Long? = null, val hlsInterstitials: Boolean = false,
    originHeaders: Map<String, Map<String,String>> = emptyMap()) {
    val originHeaders = originHeaders.mapKeys { (origin, _) ->
        require(origin == ProviderOriginHeaders.origin(origin))
        origin
    }.mapValues { (_, headers) -> headers.toMap().also(ProviderOriginHeaders::validateHeaders) }
    init { require(!(imaLive != null && hlsInterstitials)); require(expiresAt == null || expiresAt > 0) }
    override fun toString() = "ProviderMediaOptions(redacted)"
}
/** Resolve afresh on every HTTP hop; no credential inheritance across redirects or media origins. */
class ProviderOriginHeaders(scopes: Map<String, Map<String,String>>) {
    private val scopes = scopes.mapKeys { origin(it.key) }.mapValues { it.value.toMap().also(::validateHeaders) }
    val headerNames: Set<String> = this.scopes.values.flatMap { it.keys }.toSet() + setOf("Authorization", "Cookie", "Proxy-Authorization")
    fun forAddress(address: String): Map<String,String> = runCatching { scopes[origin(address)] }.getOrNull().orEmpty()
    override fun toString() = "ProviderOriginHeaders(redacted)"
    companion object {
        fun origin(address: String): String {
            require(address.length <= 16384 && address.none { it.isISOControl() })
            val uri = URI(address)
            require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawFragment == null)
            return "https://" + uri.host.lowercase(Locale.ROOT) + if(uri.port in setOf(-1,443)) "" else ":${uri.port}"
        }
        fun validateHeaders(headers: Map<String,String>) {
            require(headers.size <= 32)
            require(headers.keys.map { it.lowercase(Locale.ROOT) }.distinct().size == headers.size)
            require(headers.keys.none { it.lowercase(Locale.ROOT) in setOf("host", "connection", "content-length", "transfer-encoding", "range", "proxy-authorization") })
            require(headers.all { (name,value) -> name.matches(Regex("[A-Za-z0-9-]{1,128}")) && value.length <= 16384 && value.none { it.isISOControl() } })
        }
    }
}
