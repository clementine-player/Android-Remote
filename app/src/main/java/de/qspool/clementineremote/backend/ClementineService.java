/* This file is part of the Android Clementine Remote.
 * Copyright (C) 2013, Andreas Muttscheller <asfa194@gmail.com>
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

package de.qspool.clementineremote.backend;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.downloader.DownloadManager;
import de.qspool.clementineremote.backend.globalsearch.GlobalSearchManager;
import de.qspool.clementineremote.backend.listener.PlayerConnectionListener;
import de.qspool.clementineremote.backend.mediasession.ClementineMediaSessionNotification;
import de.qspool.clementineremote.backend.mediasession.MediaSessionController;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.streaming.Renderer;
import de.qspool.clementineremote.backend.streaming.ThisRenderer;
import de.qspool.clementineremote.backend.sync.PhonePlayer;
import de.qspool.clementineremote.backend.sync.PlayFrom;
import de.qspool.clementineremote.backend.sync.Syncer;
import de.qspool.clementineremote.utils.Utilities;

public class ClementineService extends Service {

    public final static String SERVICE_ID = "de.qspool.clementineremote.service.id";

    public final static int SERVICE_START = 1;

    public final static int SERVICE_DISCONNECTED = 2;

    public final static String EXTRA_STRING_IP = "EXTRA_IP";

    public final static String EXTRA_INT_PORT = "EXTRA_PORT";

    public final static String EXTRA_INT_AUTH = "EXTRA_AUTH";

    private final String TAG = getClass().getSimpleName();

    private NotificationManager mNotificationManager;

    private Thread mPlayerThread;

    private boolean mUseWakeLock = false;

    private PowerManager.WakeLock mWakeLock;

    private Handler mUiHandler;

    private ClementineServiceBinder mClementineServiceBinder = new ClementineServiceBinder();

    private boolean mInForeground = false;

    /** Whether Clementine is playing its music on this phone (remote streaming). */
    private boolean mPlayingHere = false;

    private MediaSessionController mMediaSessionController;

    private Renderer mRenderer;

    private volatile int mLatestStartId = -1;

    private volatile int mConnectionStartId = -1;

    private volatile ClementinePlayerConnection mActiveConnection;

    /**
     * Starts the service to connect to Clementine. Works from the background too (widget,
     * notification, Tasker): the service is started as a foreground service and goes to the
     * foreground straight away.
     *
     * @return false if Android does not allow the app to start it right now
     */
    public static boolean startConnection(Context context, Intent serviceIntent) {
        try {
            ContextCompat.startForegroundService(context, serviceIntent);
            return true;
        } catch (IllegalStateException e) {
            // Android 12+ refuses foreground service starts from the background except in
            // specific cases (ForegroundServiceStartNotAllowedException).
            Log.w("ClementineService", "Not allowed to start the service now", e);
            return false;
        }
    }

    public class ClementineServiceBinder extends Binder {

        public ClementineService getClementineService() {
            return ClementineService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Get a Wakelock Object
        PowerManager pm = (PowerManager) getSystemService(
                Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "clementine:service");
        // Non-reference-counted so repeated CONNECTED/DISCONNECTED cycles never
        // unbalance acquire/release counts or throw under-locked exceptions.
        mWakeLock.setReferenceCounted(false);

        SharedPreferences prefs = App.getPreferences();
        mUseWakeLock = prefs.getBoolean(SharedPreferencesKeys.SP_WAKE_LOCK, false);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mClementineServiceBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mLatestStartId = startId;
        if (intent != null && intent.hasExtra(EXTRA_STRING_IP)) {
            // A connect request, possibly started with startForegroundService().
            enterForeground();
        }
        if (intent != null && intent.hasExtra(SERVICE_ID)) {
            handleServiceAction(intent);
        }

        // START_NOT_STICKY avoids restarting an unconfigured zombie foreground
        // service with a null intent after OS process kill.
        return START_NOT_STICKY;
    }

    private void stopServiceForStartId(int startId) {
        // Stop only if startId is still the latest so a stale disconnect cannot
        // kill a newer SERVICE_START; fall back to stopSelf() when there is no startId.
        if (startId >= 0) {
            stopSelfResult(startId);
        } else {
            stopSelf();
        }
    }

    /**
     * Handle the requests to the service
     *
     * @param intent The action to perform
     */
    public void handleServiceAction(final Intent intent) {
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        int action = intent.getIntExtra(SERVICE_ID, 0);
        switch (action) {
            case SERVICE_START:
                // Replace a dying or dead connection instead of sending onto
                // its looper, and clear mActiveConnection before releasing so
                // late callbacks from the old connection are ignored.
                if (App.ClementineConnection != null
                        && (App.ClementineConnection.isClosedOrClosing()
                                || mPlayerThread == null
                                || !mPlayerThread.isAlive())) {
                    mActiveConnection = null;
                    if (mMediaSessionController != null) {
                        mMediaSessionController.release();
                        mMediaSessionController = null;
                    }
                    if (mRenderer != null) {
                        mRenderer.release();
                        mRenderer = null;
                    }
                    stopPlayerThread();
                    App.ClementineConnection = null;
                }
                mConnectionStartId = mLatestStartId;
                // Create a new instance
                if (App.ClementineConnection == null) {
                    final ClementinePlayerConnection connection = new ClementinePlayerConnection();
                    mActiveConnection = connection;
                    App.ClementineConnection = connection;
                    connection.setUiHandler(mUiHandler);
                    RemoteRepository.attach(connection);
                    mMediaSessionController = new MediaSessionController(this, connection);
                    mMediaSessionController.registerMediaSession();
                    mRenderer = Renderer.attach(this, connection, this::setPlayingHere);
                    Syncer.attach(connection);

                    GlobalSearchManager.getInstance().reset();

                    connection.addPlayerConnectionListener(
                            new PlayerConnectionListener() {
                                @Override
                                public void onConnectionStatusChanged(
                                        ClementinePlayerConnection.ConnectionStatus status) {
                                    if (connection != mActiveConnection) {
                                        return;
                                    }
                                    switch (status) {
                                        case IDLE:
                                            sendConnectMessageIfPossible(intent);
                                            break;
                                        case CONNECTING:
                                        case RECONNECTING:
                                            enterForeground();
                                            break;
                                        case NO_CONNECTION: {
                                            // Only drop foreground if no newer
                                            // start command superseded this one.
                                            int stopId = mConnectionStartId;
                                            if (mLatestStartId == stopId) {
                                                leaveForeground(false);
                                            }
                                            stopServiceForStartId(stopId);
                                            break;
                                        }
                                        case CONNECTED:
                                        case RECONNECTED:
                                            enterForeground();
                                            acquireWakeLock();
                                            break;
                                        case LOST_CONNECTION:
                                            showKeepAliveDisconnectNotification();
                                            break;
                                        case DISCONNECTED: {
                                            // Only detach/stop for the startId
                                            // that owned this connection.
                                            int stopId = mConnectionStartId;
                                            if (mLatestStartId == stopId) {
                                                leaveForeground(true);
                                            }
                                            stopServiceForStartId(stopId);
                                            releaseWakeLock();
                                            break;
                                        }
                                    }
                                }

                                @Override
                                public void onClementineMessageReceived(
                                        ClementineMessage clementineMessage) {
                                    if (connection != mActiveConnection) {
                                        return;
                                    }
                                    GlobalSearchManager.getInstance().parseClementineMessage(
                                            clementineMessage);
                                    if (startsPlaying(clementineMessage)) {
                                        ContextCompat.getMainExecutor(ClementineService.this).execute(
                                                () -> PhonePlayer.INSTANCE.playFrom(
                                                        ClementineService.this, PlayFrom.CLEMENTINE));
                                    }
                                }
                            });

                    mPlayerThread = new Thread(connection);
                    mPlayerThread.start();
                } else {
                    mActiveConnection = App.ClementineConnection;
                    sendConnectMessageIfPossible(intent);
                }
                break;
            case SERVICE_DISCONNECTED:
                stopServiceForStartId(mLatestStartId);
                break;
            default:
                break;
        }
    }

    /**
     * Whether the message says Clementine started playing, such as a song from the library: the
     * app then plays from it again, pausing the phone, so the player controls what's playing.
     */
    @VisibleForTesting
    static boolean startsPlaying(ClementineMessage message) {
        return !message.isErrorMessage() && message.getMessageType() == MsgType.PLAY;
    }

    @Override
    public void onDestroy() {
        ClementinePlayerConnection connection = App.ClementineConnection;
        Handler handler = connection != null ? connection.mHandler : null;
        if (connection != null && connection.isConnected() && handler != null) {
            // Move the request to the message
            Message msg = Message.obtain();
            msg.obj = ClementineMessage.getMessage(MsgType.DISCONNECT);

            // Send the request to the thread
            handler.sendMessage(msg);
        }
        interruptThread();
        // Release session, renderer, and wake lock here too because OS kill or
        // unbind can destroy the service without a clean DISCONNECTED event.
        if (mMediaSessionController != null) {
            mMediaSessionController.release();
            mMediaSessionController = null;
        }
        if (mRenderer != null) {
            mRenderer.release();
            mRenderer = null;
        }
        releaseWakeLock();
        mActiveConnection = null;
        App.ClementineConnection = null;
        leaveForeground(false);
        super.onDestroy();
    }

    private void acquireWakeLock() {
        if (mUseWakeLock && mWakeLock != null && !mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    private Notification buildConnectingNotification() {
        return new NotificationCompat.Builder(this, App.notificationChannel)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.connectdialog_connecting))
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(Utilities.getClementineRemotePendingIntent(this))
                .build();
    }

    /**
     * Keeps the connection alive while the app is in the background. The notification is
     * replaced by the player notification (same id) once Clementine sends the current track.
     */
    private synchronized void enterForeground() {
        if (mInForeground) {
            return;
        }
        ServiceCompat.startForeground(this, ClementineMediaSessionNotification.NOTIFIFCATION_ID,
                buildConnectingNotification(), foregroundServiceTypes());
        mInForeground = true;
    }

    /**
     * Connected to Clementine, and playing media too while Clementine plays on this phone.
     * ServiceCompat ignores the types before Android 10, which introduced them.
     */
    @SuppressLint("InlinedApi")
    private int foregroundServiceTypes() {
        return mPlayingHere
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                        | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                : ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
    }

    /**
     * Updates the foreground service's types as Clementine starts and stops playing on this
     * phone, keeping the notification showing.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public synchronized void setPlayingHere(boolean playingHere) {
        if (playingHere == mPlayingHere) {
            return;
        }
        mPlayingHere = playingHere;
        if (!mInForeground) {
            return;
        }
        NotificationManager notifications = getSystemService(NotificationManager.class);
        if (notifications != null) {
            for (StatusBarNotification shown : notifications.getActiveNotifications()) {
                if (shown.getId() == ClementineMediaSessionNotification.NOTIFIFCATION_ID) {
                    ServiceCompat.startForeground(this, shown.getId(), shown.getNotification(),
                            foregroundServiceTypes());
                    return;
                }
            }
        }
        // Fallback to the connecting notification if the media notification is
        // not active yet so Android still gets the updated FGS type.
        ServiceCompat.startForeground(this, ClementineMediaSessionNotification.NOTIFIFCATION_ID,
                buildConnectingNotification(), foregroundServiceTypes());
    }

    /**
     * @param keepNotification leave the notification to the player notification code, which
     *                         may be showing "connection lost"; otherwise remove it
     */
    private synchronized void leaveForeground(boolean keepNotification) {
        if (!mInForeground) {
            return;
        }
        ServiceCompat.stopForeground(this, keepNotification
                ? ServiceCompat.STOP_FOREGROUND_DETACH
                : ServiceCompat.STOP_FOREGROUND_REMOVE);
        mInForeground = false;
    }

    public void setUiHandler(Handler uiHandler) {
        mUiHandler = uiHandler;
    }

    private void stopPlayerThread() {
        if (mPlayerThread != null) {
            mPlayerThread.interrupt();
        }

        ClementinePlayerConnection connection = App.ClementineConnection;
        Handler handler = connection != null ? connection.mHandler : null;
        if (handler != null
                && mPlayerThread != null
                && mPlayerThread.isAlive()) {
            handler.post(new Runnable() {

                @Override
                public void run() {
                    Looper looper = Looper.myLooper();
                    if (looper != null) {
                        looper.quit();
                    }
                }

            });
        }
    }

    private void interruptThread() {
        stopPlayerThread();
        DownloadManager.getInstance().shutdown();
    }

    /**
     * Create a notification that shows, that we got a keep alive timeout
     */
    @SuppressLint("InlinedApi")
    private void showKeepAliveDisconnectNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    App.notificationChannel, "Default",
                    NotificationManager.IMPORTANCE_LOW
            );

            mNotificationManager.createNotificationChannel(notificationChannel);
        }

         Notification notification = new NotificationCompat.Builder(this, App.notificationChannel)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_disconnect_keep_alive))
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(Utilities.getClementineRemotePendingIntent(this))
                .build();
        mNotificationManager
                .notify(ClementineMediaSessionNotification.NOTIFIFCATION_ID, notification);
    }

    private void sendConnectMessageIfPossible(Intent intent) {
        ClementinePlayerConnection connection = App.ClementineConnection;
        Handler handler = connection != null ? connection.mHandler : null;
        if (intent != null && intent.hasExtra(EXTRA_STRING_IP) && handler != null) {
            final String ip = intent.getStringExtra(EXTRA_STRING_IP);
            final int port = intent.getIntExtra(EXTRA_INT_PORT, 0);
            final int auth = intent.getIntExtra(EXTRA_INT_AUTH, 0);

            Message msg = Message.obtain();
            msg.obj = ClementineMessageFactory
                    .buildConnectMessage(ip, port, auth, true, false,
                            ThisRenderer.capabilitiesIfEnabled());
            handler.sendMessage(msg);
        }
    }
}
