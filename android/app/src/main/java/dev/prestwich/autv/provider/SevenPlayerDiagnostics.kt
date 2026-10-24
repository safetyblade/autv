package dev.prestwich.autv.provider

import androidx.media3.common.PlaybackException
import dev.prestwich.autv.data.provider.ProviderPlaybackStage

/** Classify numeric Media3 codes only; never expose its URL-bearing error/cause text. */
internal fun sevenPlayerFailureStage(code: Int): ProviderPlaybackStage = when {
    code in 6000..6999 -> ProviderPlaybackStage.SEVEN_DRM
    code in setOf(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) -> ProviderPlaybackStage.SEVEN_MANIFEST
    else -> ProviderPlaybackStage.SEVEN_RENDERER
}
