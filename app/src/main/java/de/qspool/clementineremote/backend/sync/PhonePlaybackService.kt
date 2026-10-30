package de.qspool.clementineremote.backend.sync

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import de.qspool.clementineremote.R
import de.qspool.clementineremote.utils.Utilities

/**
 * Plays the songs kept on this phone, without Clementine: ExoPlayer in a media session, so the
 * notification, lock screen, Bluetooth and watches show and control it, as for any music player.
 * The notification's header says it plays "On this phone". [PhonePlayer] starts it.
 */
@OptIn(UnstableApi::class)
class PhonePlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus= */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        session = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .setSessionActivity(Utilities.getClementineRemotePendingIntent(this))
            .setCallback(object : MediaSession.Callback {
                // Controllers send items without their URIs, so each says its own in its request.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.mapTo(mutableListOf()) { item ->
                        item.requestMetadata.mediaUri?.let { item.buildUpon().setUri(it).build() } ?: item
                    }
                )
            })
            .build()
        setMediaNotificationProvider(OnThisPhoneNotification(this))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Media3's notification, with "On this phone" in its header. */
    private class OnThisPhoneNotification(private val context: Context) : DefaultMediaNotificationProvider(context) {

        init {
            setSmallIcon(R.drawable.notification)
        }

        override fun addNotificationActions(
            mediaSession: MediaSession,
            mediaButtons: ImmutableList<CommandButton>,
            builder: NotificationCompat.Builder,
            actionFactory: MediaNotification.ActionFactory,
        ): IntArray {
            builder.setSubText(context.getString(R.string.sync_on_this_phone))
            return super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
        }
    }

    companion object {
        const val SESSION_ID = "on-this-phone"
    }
}
