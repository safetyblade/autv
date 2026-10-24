package dev.prestwich.autv.provider

import androidx.media3.common.PlaybackException
import dev.prestwich.autv.data.provider.ProviderPlaybackStage
import org.junit.Assert.assertEquals
import org.junit.Test

class SevenPlayerDiagnosticsTest {
    @Test fun mediaAndLicenceFailuresAreDistinguishedWithoutUsingErrorText() {
        assertEquals(ProviderPlaybackStage.SEVEN_MANIFEST, sevenPlayerFailureStage(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED))
        assertEquals(ProviderPlaybackStage.SEVEN_DRM, sevenPlayerFailureStage(PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED))
        assertEquals(ProviderPlaybackStage.SEVEN_RENDERER, sevenPlayerFailureStage(PlaybackException.ERROR_CODE_DECODING_FAILED))
    }
}
