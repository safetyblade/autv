package dev.prestwich.autv.guide

import dev.prestwich.autv.data.CastLoadFailure

/** Fixed labels/numbers only: SDK messages/customData may contain private signed URLs. */
object CastDiagnostics {
    fun failure(kind: CastLoadFailure,status: Int?=null,detail: Int?=null,http: Boolean=false,reason: String?=null): String {
        val description=when(kind) {
            CastLoadFailure.URL_UNAVAILABLE -> if(http) "Sender could not access the media URL" else "Media URL unavailable or refused by receiver"
            CastLoadFailure.UNSUPPORTED_FORMAT -> "Unsupported media format reported or detected"
            CastLoadFailure.SENDER_REQUIREMENTS -> "Stream requires sender-specific URL parameters"
            CastLoadFailure.RECEIVER_REJECTED -> "Receiver rejected the media load"
            CastLoadFailure.TIMEOUT -> "No confirmed receiver playback within 30 seconds"
            CastLoadFailure.REMOTE_STOPPED -> "Receiver stopped media playback"
        }
        return "Cast ${kind.name}: $description"+(status?.let { if(http) " (HTTP $it)" else " (code $it)" } ?: "")+
            (detail?.let { " (detail $it)" } ?: "")+
            (reason?.takeIf { it in setOf("NOT_SUPPORTED","AUTHENTICATION_EXPIRED","NOT_AVAILABLE_IN_REGION","PREMIUM_ACCOUNT_REQUIRED","INVALID_PARAMS","INVALID_REQUEST","GENERIC_LOAD_ERROR","VIDEO_DEVICE_REQUIRED","APP_ERROR","CONCURRENT_STREAM_LIMIT") }?.let { " [$it]" } ?: "")+". Playing on this device."
    }
}
