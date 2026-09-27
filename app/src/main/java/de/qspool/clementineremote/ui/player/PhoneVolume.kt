package de.qspool.clementineremote.ui.player

import android.content.Context
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
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return volume
}
