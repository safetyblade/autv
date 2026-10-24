package dev.prestwich.autv.data.provider

import org.json.JSONObject
import org.json.JSONTokener

/** Only enum labels survive parsing; never retain a body, message, URL or provider value. */
class SevenDescriptorRejection private constructor(val reason: Reason, val fields: Set<Field>) {
    enum class Reason(val label: String) {
        LOCATION("location_verification_required"), MARKET("video_not_in_market"),
        UNRECOGNISED("no recognised provider error code"), NON_JSON("non-JSON response"),
        TOO_LARGE("response exceeds diagnostic limit"), UNREADABLE("response unavailable")
    }
    enum class Field(val label: String) {
        ACCOUNT("accountId"), REFERENCE("referenceId"), APP("appId"), DEVICE_TYPE("deviceType"),
        PLATFORM("platformType"), DEVICE("deviceId"), MARKET("marketId"), VIDEO("videoType"),
        POSTCODE("pc"), ADVERT("advertid"), DELIVERY("deliveryId"), OZ("ozid"), SDK("sdkverification"),
        ENCRYPTION("cp.encryptionType"), DRM("cp.drmSystems"), CONTAINER("cp.containerFormat"),
        CODECS("cp.supportedCodecs"), DRM_AUTH("cp.drmAuth")
    }
    val summary: String get() = "Descriptor rejection: ${reason.label}." +
        if (fields.isEmpty()) " No allowlisted field hints." else
            " Response field hints (not a confirmed cause): ${fields.joinToString { it.label }}."
    override fun toString() = summary
    companion object {
        const val MAX_BYTES = 16384
        fun unavailable() = SevenDescriptorRejection(Reason.UNREADABLE, emptySet())
        fun oversized() = SevenDescriptorRejection(Reason.TOO_LARGE, emptySet())
        fun read(body: String): SevenDescriptorRejection {
            if (body.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return oversized()
            val root = runCatching { JSONTokener(body).nextValue() }.getOrNull()
            val records = when (root) {
                is JSONObject -> listOf(root)
                is org.json.JSONArray -> (0 until minOf(root.length(), 32)).mapNotNull { root.optJSONObject(it) }
                else -> return SevenDescriptorRejection(Reason.NON_JSON, emptySet())
            }
            // The two codes are explicitly handled by packaged SlyGuy 7plus api.play.
            val codes = records.flatMap { listOf(it.optString("error"), it.optString("error_code")) }
            val reason = when {
                "location_verification_required" in codes -> Reason.LOCATION
                "video_not_in_market" in codes -> Reason.MARKET
                else -> Reason.UNRECOGNISED
            }
            val hints = mutableSetOf<Field>()
            fun inspect(json: JSONObject) {
                for (key in listOf("field", "path", "parameter", "param", "message", "description", "error", "errorMessage")) {
                    val text = json.opt(key) as? String ?: continue
                    Field.entries.filterTo(hints) {
                        Regex("(?<![A-Za-z0-9_.])" + Regex.escape(it.label) + "(?![A-Za-z0-9_.])").containsMatchIn(text)
                    }
                }
            }
            for (record in records) {
                inspect(record)
                for (key in listOf("errors", "validationErrors", "details")) {
                    record.optJSONObject(key)?.let { errors ->
                        Field.entries.filterTo(hints) { errors.has(it.label) }
                    }
                    val errors = record.optJSONArray(key) ?: continue
                    for (i in 0 until minOf(errors.length(), 32)) errors.optJSONObject(i)?.let { inspect(it) }
                }
            }
            return SevenDescriptorRejection(reason, hints.toSet())
        }
    }
}
