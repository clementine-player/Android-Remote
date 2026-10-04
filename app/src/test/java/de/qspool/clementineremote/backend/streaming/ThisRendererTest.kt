package de.qspool.clementineremote.backend.streaming

import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RendererFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThisRendererTest {

    @Test
    fun describesThisPhone() {
        val capabilities = ThisRenderer.capabilities(App.getApp())

        // The same id each time, so Clementine recognises the phone.
        assertEquals(ThisRenderer.id(), capabilities.rendererId)
        assertEquals(capabilities.rendererId, ThisRenderer.capabilities(App.getApp()).rendererId)
        assertTrue(capabilities.displayName.isNotBlank())
        // ExoPlayer plays WAV without a decoder, so it's always there.
        assertTrue(capabilities.formatsList.any { it.mimeType == "audio/wav" })
        assertEquals(
            listOf(
                RendererFeature.RENDERER_FEATURE_GAPLESS,
                RendererFeature.RENDERER_FEATURE_HTTP_RANGE,
                RendererFeature.RENDERER_FEATURE_RELATIVE_URLS,
            ),
            capabilities.featuresList,
        )
    }

    @Test
    fun offersNothingWhenTurnedOff() {
        App.getPreferences().edit().putBoolean(SharedPreferencesKeys.SP_RENDERER, false).commit()

        assertNull(ThisRenderer.capabilitiesIfEnabled())
    }
}
