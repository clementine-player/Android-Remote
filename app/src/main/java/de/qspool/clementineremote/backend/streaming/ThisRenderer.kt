package de.qspool.clementineremote.backend.streaming

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.AudioFormat
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RendererCapabilities
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RendererFeature
import java.util.UUID

/**
 * This phone as a renderer: an audio output Clementine can play on (remote streaming). It
 * registers when connecting, with what it can play ([capabilities]), and Clementine lists it
 * among its outputs.
 */
object ThisRenderer {

    /** What Clementine sends, mapped from the Android decoder that plays it. */
    private val FORMATS = listOf(
        MediaFormat.MIMETYPE_AUDIO_MPEG to listOf("audio/mpeg"),
        MediaFormat.MIMETYPE_AUDIO_AAC to listOf("audio/mp4", "audio/aac"),
        MediaFormat.MIMETYPE_AUDIO_FLAC to listOf("audio/flac", "audio/ogg; codecs=flac"),
        MediaFormat.MIMETYPE_AUDIO_VORBIS to listOf("audio/ogg; codecs=vorbis"),
        MediaFormat.MIMETYPE_AUDIO_OPUS to listOf("audio/ogg; codecs=opus"),
        MediaFormat.MIMETYPE_AUDIO_RAW to listOf("audio/wav"),
    )

    /** Whether Clementine may play on this phone; on unless turned off in the settings. */
    @JvmStatic
    val isEnabled: Boolean
        get() = App.getPreferences().getBoolean(SharedPreferencesKeys.SP_RENDERER, true)

    /** Stable for this install, so Clementine recognises the phone when it reconnects. */
    @JvmStatic
    fun id(): String {
        val preferences = App.getPreferences()
        preferences.getString(SharedPreferencesKeys.SP_RENDERER_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        preferences.edit { putString(SharedPreferencesKeys.SP_RENDERER_ID, id) }
        return id
    }

    /** The name Clementine shows for the phone: the one set in Android, or its model. */
    private fun displayName(context: Context): String {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        } else {
            null
        }
        return name?.takeIf { it.isNotBlank() } ?: Build.MODEL
    }

    /** What to send when connecting, or null when Clementine may not play here. */
    @JvmStatic
    fun capabilitiesIfEnabled(): RendererCapabilities? =
        if (isEnabled) capabilities(App.getApp()) else null

    @JvmStatic
    fun capabilities(context: Context): RendererCapabilities {
        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder }
            .flatMap { it.supportedTypes.asList() }
            .map { it.lowercase() }
            .toSet()
        val builder = RendererCapabilities.newBuilder()
            .setRendererId(id())
            .setDisplayName(displayName(context))
            // ExoPlayer plays a queued item without a gap, and seeks with Range requests.
            .addFeatures(RendererFeature.RENDERER_FEATURE_GAPLESS)
            .addFeatures(RendererFeature.RENDERER_FEATURE_HTTP_RANGE)
        for ((decoder, mimeTypes) in FORMATS) {
            // ExoPlayer reads WAV itself, and plays PCM without a decoder.
            if (decoder != MediaFormat.MIMETYPE_AUDIO_RAW && decoder !in decoders) continue
            for (mimeType in mimeTypes) {
                builder.addFormats(AudioFormat.newBuilder().setMimeType(mimeType))
            }
        }
        return builder.build()
    }
}
