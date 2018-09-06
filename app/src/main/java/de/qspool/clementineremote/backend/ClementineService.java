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
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

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
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Clementine");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mUseWakeLock = prefs.getBoolean(SharedPreferencesKeys.SP_WAKE_LOCK, false);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mClementineServiceBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra(SERVICE_ID)) {
            handleServiceAction(intent);
        }

        return START_STICKY;
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
                // Create a new instance
                if (App.ClementineConnection == null) {
                    App.ClementineConnection = new ClementinePlayerConnection();
                    App.ClementineConnection.setUiHandler(mUiHandler);
                    MediaSessionController mediaSessionController = new MediaSessionController(this,
                            App.ClementineConnection);
                    mediaSessionController.registerMediaSession();

                    GlobalSearchManager.getInstance().reset();

                    App.ClementineConnection.addPlayerConnectionListener(
                            new PlayerConnectionListener() {
                                @Override
                                public void onConnectionStatusChanged(
                                        ClementinePlayerConnection.ConnectionStatus status) {
                                    switch (status) {
                                        case IDLE:
                                            sendConnectMessageIfPossible(intent);
                                            break;
                                        case CONNECTING:
                                            break;
                                        case NO_CONNECTION:
                                            sendDisconnectServiceMessage();
                                            break;
                                        case CONNECTED:
                                            if (mUseWakeLock) {
                                                mWakeLock.acquire();
                                            }
                                            break;
                                        case LOST_CONNECTION:
                                            showKeepAliveDisconnectNotification();
                                            break;
                                        case DISCONNECTED:
                                            sendDisconnectServiceMessage();

                                            if (mUseWakeLock) {
                                                mWakeLock.release();
                                            }
                                            break;
                                    }
                                }

                                @Override
                                public void onClementineMessageReceived(
                                        ClementineMessage clementineMessage) {
                                    GlobalSearchManager.getInstance().parseClementineMessage(
                                            clementineMessage);
                                }
                            });

                    mPlayerThread = new Thread(App.ClementineConnection);
                    mPlayerThread.start();
                } else {
                    sendConnectMessageIfPossible(intent);
                }
                break;
            case SERVICE_DISCONNECTED:
                stopSelf();
                break;
            default:
                break;
        }
    }

    @Override
    public void onDestroy() {
        if (App.ClementineConnection != null
                && App.ClementineConnection.isConnected()) {
            // Move the request to the message
            Message msg = Message.obtain();
            msg.obj = ClementineMessage.getMessage(MsgType.DISCONNECT);

            // Send the request to the thread
            App.ClementineConnection.mHandler.sendMessage(msg);
        }
        interruptThread();
        App.ClementineConnection = null;
    }

    public void setUiHandler(Handler uiHandler) {
        mUiHandler = uiHandler;
    }

    private void sendDisconnectServiceMessage() {
        Intent mServiceIntent = new Intent(
                ClementineService.this,
                ClementineService.class);
        mServiceIntent
                .putExtra(SERVICE_ID, SERVICE_DISCONNECTED);
        startService(mServiceIntent);
    }

    private void interruptThread() {
        if (mPlayerThread != null) {
            mPlayerThread.interrupt();
        }

        if (App.ClementineConnection != null
                && App.ClementineConnection.mHandler != null
                && mPlayerThread.isAlive()) {
            App.ClementineConnection.mHandler.post(new Runnable() {

                @Override
                public void run() {
                    Looper.myLooper().quit();
                }

            });
        }

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
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(Utilities.getClementineRemotePendingIntent(this))
                .build();
        mNotificationManager
                .notify(ClementineMediaSessionNotification.NOTIFIFCATION_ID, notification);
    }

    private void sendConnectMessageIfPossible(Intent intent) {
        if (intent.hasExtra(EXTRA_STRING_IP)) {
            final String ip = intent.getStringExtra(EXTRA_STRING_IP);
            final int port = intent.getIntExtra(EXTRA_INT_PORT, 0);
            final int auth = intent.getIntExtra(EXTRA_INT_AUTH, 0);

            Message msg = Message.obtain();
            msg.obj = ClementineMessageFactory
                    .buildConnectMessage(ip, port, auth, true, false);
            App.ClementineConnection.mHandler.sendMessage(msg);
        }
    }
}
