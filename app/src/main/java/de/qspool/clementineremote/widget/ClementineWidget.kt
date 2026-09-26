/* This file is part of the Android Clementine Remote.
 * Copyright (C) 2014, Andreas Muttscheller <asfa194@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package de.qspool.clementineremote.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.runtime.collectAsState
import de.qspool.clementineremote.App
import de.qspool.clementineremote.BuildConfig
import de.qspool.clementineremote.R
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.ClementinePlayerConnection.ConnectionStatus
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.RemoteRepository.NowPlaying
import de.qspool.clementineremote.backend.mediasession.ClementineMediaSessionNotification
import de.qspool.clementineremote.backend.receivers.ClementineBroadcastReceiver
import de.qspool.clementineremote.ui.ConnectActivity
import de.qspool.clementineremote.ui.MainActivity
import de.qspool.clementineremote.ui.theme.ClementineDarkColors
import de.qspool.clementineremote.ui.theme.ClementineLightColors
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The home-screen widget, in Glance: the song playing with play/pause and next while connected;
 * otherwise a tap to connect to the last Clementine, or to open the app. It follows
 * [RemoteRepository], in Clementine's colours, light or dark with the system.
 */
class ClementineWidget : GlanceAppWidget() {

    // A narrow one keeps to the song and play/pause; a wider one adds the icon and next.
    override val sizeMode = SizeMode.Responsive(setOf(NARROW, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val host = App.getPreferences().getString(SharedPreferencesKeys.SP_KEY_IP, null)
        provideContent {
            val connection by RemoteRepository.connection.collectAsState()
            val nowPlaying by RemoteRepository.nowPlaying.collectAsState()
            GlanceTheme(colors = colors) {
                WidgetContent(widgetState(connection, nowPlaying, host))
            }
        }
    }

    override val previewSizeMode = SizeMode.Responsive(setOf(NARROW, WIDE))

    /** The widget picker's preview (Android 15 and later): a song playing, in both sizes. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme(colors = colors) { WidgetContent(PREVIEW) }
        }
    }

    companion object {
        internal val NARROW = DpSize(160.dp, 56.dp)
        internal val WIDE = DpSize(260.dp, 56.dp)

        internal val colors = ColorProviders(light = ClementineLightColors, dark = ClementineDarkColors)

        private const val TAG = "ClementineWidget"

        /** Where the widget is updated from: a failure there is logged, never taken down the app. */
        private val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, e -> Log.w(TAG, "Couldn't update the widget", e) },
        )

        private val PREVIEW = WidgetState(
            WidgetText.Plain("Clair de lune"),
            WidgetText.Plain("Claude Debussy · Suite bergamasque"),
            WidgetTap.OPEN_PLAYER,
            controls = true,
            playing = true,
        )

        /**
         * Gives the widget picker the preview, once for each version of the app: Android limits how
         * often an app may set its previews.
         */
        @JvmStatic
        fun publishPreview(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                return
            }
            val app = context.applicationContext
            scope.launch {
                val preferences = App.getPreferences()
                if (preferences.getInt(PREVIEW_VERSION, 0) == BuildConfig.VERSION_CODE) {
                    return@launch
                }
                val result = GlanceAppWidgetManager(app).setWidgetPreviews(ClementineWidgetProvider::class)
                if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
                    preferences.edit { putInt(PREVIEW_VERSION, BuildConfig.VERSION_CODE) }
                }
            }
        }

        /** The app version whose widget preview was last given to the picker. */
        private const val PREVIEW_VERSION = "widget_preview_version"

        /** Redraws the widgets placed, if any, after the connection or the song changes. */
        @JvmStatic
        fun update(context: Context) {
            val app = context.applicationContext
            scope.launch { ClementineWidget().updateAll(app) }
        }
    }
}

/** What a tap on the widget does. */
internal enum class WidgetTap { CONNECT, OPEN_CONNECT, OPEN_PLAYER }

/** What the widget shows: text resources, or the song's own text. */
internal data class WidgetState(
    val title: WidgetText,
    val subtitle: WidgetText?,
    val tap: WidgetTap,
    /** Whether play/pause and next are there: while connected. */
    val controls: Boolean = false,
    val playing: Boolean = false,
)

internal sealed interface WidgetText {
    data class Resource(val id: Int) : WidgetText
    data class Plain(val text: String) : WidgetText
}

/** The widget for [connection] and [nowPlaying]; [host] is the last Clementine, if any. */
internal fun widgetState(connection: ConnectionStatus, nowPlaying: NowPlaying, host: String?): WidgetState =
    when (connection) {
        ConnectionStatus.CONNECTED -> {
            val song = nowPlaying.song
            WidgetState(
                title = song?.title?.let { WidgetText.Plain(it) } ?: WidgetText.Resource(R.string.player_nosong),
                subtitle = song?.let {
                    WidgetText.Plain(listOf(it.artist, it.album).filter { part -> !part.isNullOrBlank() }.joinToString(" · "))
                },
                tap = WidgetTap.OPEN_PLAYER,
                controls = true,
                playing = nowPlaying.isPlaying,
            )
        }
        ConnectionStatus.CONNECTING ->
            WidgetState(WidgetText.Resource(R.string.connectdialog_connecting), null, WidgetTap.OPEN_CONNECT)
        ConnectionStatus.NO_CONNECTION -> WidgetState(
            WidgetText.Resource(R.string.widget_couldnt_connect),
            WidgetText.Resource(R.string.widget_open_clementine),
            WidgetTap.OPEN_CONNECT,
        )
        // Not connected: connect to the last Clementine, or open the app to pick one.
        else -> if (host.isNullOrBlank()) {
            WidgetState(
                WidgetText.Resource(R.string.widget_not_connected),
                WidgetText.Resource(R.string.widget_open_clementine),
                WidgetTap.OPEN_CONNECT,
            )
        } else {
            WidgetState(WidgetText.Resource(R.string.widget_connect_to), WidgetText.Plain(host), WidgetTap.CONNECT)
        }
    }

@Composable
internal fun WidgetContent(state: WidgetState) {
    val context = LocalContext.current
    val wide = LocalSize.current.width >= ClementineWidget.WIDE.width
    Row(
        GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(tapAction(context, state.tap))
            .semantics { testTag = "widget" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (wide) {
            Image(ImageProvider(R.drawable.ic_launcher), contentDescription = null, modifier = GlanceModifier.size(40.dp))
            Spacer(GlanceModifier.width(12.dp))
        }
        Column(GlanceModifier.defaultWeight()) {
            Text(
                state.title.resolve(context),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.semantics { testTag = "widgetTitle" },
            )
            state.subtitle?.let {
                Text(
                    it.resolve(context),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                    modifier = GlanceModifier.semantics { testTag = "widgetSubtitle" },
                )
            }
        }
        if (state.controls) {
            Spacer(GlanceModifier.width(8.dp))
            CircleIconButton(
                imageProvider = ImageProvider(if (state.playing) R.drawable.ic_player_pause else R.drawable.ic_player_play),
                contentDescription = context.getString(if (state.playing) R.string.notification_pause else R.string.notification_play),
                onClick = broadcast(context, if (state.playing) ClementineBroadcastReceiver.PAUSE else ClementineBroadcastReceiver.PLAY),
                backgroundColor = GlanceTheme.colors.primaryContainer,
                contentColor = GlanceTheme.colors.onPrimaryContainer,
                modifier = GlanceModifier.semantics { testTag = "widgetPlayPause" },
            )
            if (wide) {
                CircleIconButton(
                    imageProvider = ImageProvider(R.drawable.ic_player_next),
                    contentDescription = context.getString(R.string.notification_next),
                    onClick = broadcast(context, ClementineBroadcastReceiver.NEXT),
                    backgroundColor = null,
                    contentColor = GlanceTheme.colors.onSurface,
                    modifier = GlanceModifier.semantics { testTag = "widgetNext" },
                )
            }
        }
    }
}

private fun WidgetText.resolve(context: Context): String = when (this) {
    is WidgetText.Resource -> context.getString(id)
    is WidgetText.Plain -> text
}

/** A broadcast to [ClementineBroadcastReceiver], which sends the command on to Clementine. */
private fun broadcast(context: Context, action: String): Action =
    actionSendBroadcast(Intent(context, ClementineBroadcastReceiver::class.java).setAction(action))

private fun tapAction(context: Context, tap: WidgetTap): Action = when (tap) {
    WidgetTap.CONNECT -> broadcast(context, ClementineBroadcastReceiver.CONNECT)
    WidgetTap.OPEN_CONNECT -> actionStartActivity(Intent(context, ConnectActivity::class.java))
    // Straight to the player, as the notification does.
    WidgetTap.OPEN_PLAYER -> actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ClementineMediaSessionNotification.EXTRA_NOTIFICATION_ID, -1),
    )
}
