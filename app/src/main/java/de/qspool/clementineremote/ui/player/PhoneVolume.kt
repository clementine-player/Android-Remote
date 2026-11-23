package de.qspool.clementineremote.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/** The phone's media volume, from 0 to 100: what plays when Clementine plays on this phone. */
internal object PhoneVolume {

    fun percent(context: Context): Int {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max else 0
    }

    fun set(context: Context, percent: Int) {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (percent.coerceIn(0, 100) * max / 100f).roundToInt(), 0)
    }
}

/** [PhoneVolume], kept up to date however it's changed: here, with the volume keys, or elsewhere. */
@Composable
internal fun rememberPhoneVolume(): State<Int> {
    val context = LocalContext.current
    val volume = remember { mutableIntStateOf(PhoneVolume.percent(context)) }
    DisposableEffect(context) {
        // Android records each stream's volume in the system settings, so a change shows there.
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                volume.intValue = PhoneVolume.percent(context)
            }
        }
        // Settings.System doesn't fire for Bluetooth A2DP/LE Audio volume changes; also observe
        // the system VOLUME_CHANGED_ACTION broadcast for STREAM_MUSIC.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val stream = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                if (stream == AudioManager.STREAM_MUSIC || stream == -1) {
                    volume.intValue = PhoneVolume.percent(context)
                }
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_VOLUME_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Ignore if already unregistered during rapid composition/context teardown.
            }
        }
    }
    return volume
}

private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
