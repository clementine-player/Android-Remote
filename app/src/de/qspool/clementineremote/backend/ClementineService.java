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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.listener.PlayerConnectionListener;
import de.qspool.clementineremote.backend.mediasession.MediaSessionController;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessage.ErrorMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.ui.MainActivity;

public class ClementineService extends Service {

    public final static String SERVICE_ID = "ServiceIntentId";

    public final static String SERVICE_DISCONNECT_DATA = "ServiceIntentData";

    public final static int SERVICE_START = 1;

    public final static int SERVICE_DISCONNECTED = 2;

    public final static String EXTRA_STRING_IP = "EXTRA_IP";

    public final static String EXTRA_INT_PORT = "EXTRA_PORT";

    public final static String EXTRA_INT_AUTH = "EXTRA_AUTH";

    private final String TAG = getClass().getSimpleName();

    private NotificationManager mNotificationManager;

    private MediaSessionController mMediaSessionController;

    private Thread mPlayerThread;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            handleServiceAction(intent);
        }

        return START_STICKY;
    }

    /**
     * Handle the requests to the service
     *
     * @param intent The action to perform
     */
    private void handleServiceAction(final Intent intent) {
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        int action = intent.getIntExtra(SERVICE_ID, 0);
        switch (action) {
            case SERVICE_START:
                // Create a new instance
                if (App.mClementineConnection == null) {
                    App.mClementineConnection = new ClementinePlayerConnection();
                    mMediaSessionController = new MediaSessionController(this,
                            App.mClementineConnection);
                    mMediaSessionController.registerMediaSession();

                    App.mClementineConnection.addPlayerConnectionListener(
                            new PlayerConnectionListener() {
                                @Override
                                public void onThreadStarted() {
                                    sendConnectMessageIfPossible(intent);
                                }

                                @Override
                                public void onConnected() {
                                }

                                @Override
                                public void onConnectionClosed(
                                        ClementineMessage clementineMessage) {
                                    Intent mServiceIntent = new Intent(ClementineService.this,
                                            ClementineService.class);
                                    mServiceIntent
                                            .putExtra(SERVICE_ID, SERVICE_DISCONNECTED);
                                    mServiceIntent.putExtra(SERVICE_DISCONNECT_DATA,
                                            clementineMessage.getErrorMessage().ordinal());
                                    startService(mServiceIntent);
                                }

                                @Override
                                public void onClementineMessageReceived(
                                        ClementineMessage clementineMessage) {
                                }
                            });

                    mPlayerThread = new Thread(App.mClementineConnection);
                    mPlayerThread.start();
                } else {
                    sendConnectMessageIfPossible(intent);
                }
                break;
            case SERVICE_DISCONNECTED:
                intteruptThread();
                App.mClementineConnection = null;

                // Check if we lost connection due a keep alive
                if (intent.hasExtra(SERVICE_DISCONNECT_DATA)) {
                    int reason = intent.getIntExtra(SERVICE_DISCONNECT_DATA, 0);
                    if (reason == ErrorMessage.KEEP_ALIVE_TIMEOUT.ordinal()
                            || reason == ErrorMessage.IO_EXCEPTION.ordinal()) {
                        showKeepAliveDisconnectNotification();
                    }
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onDestroy() {
        if (App.mClementineConnection != null
                && App.mClementineConnection.isConnected()) {
            // Create a new request

            // Move the request to the message
            Message msg = Message.obtain();
            msg.obj = ClementineMessage.getMessage(MsgType.DISCONNECT);

            // Send the request to the thread
            App.mClementineConnection.mHandler.sendMessage(msg);
        }
        intteruptThread();
        App.mClementineConnection = null;
    }

    private void intteruptThread() {
        if (mPlayerThread != null) {
            mPlayerThread.interrupt();
        }

        if (App.mClementineConnection != null
                && mPlayerThread.isAlive()) {
            App.mClementineConnection.mHandler.post(new Runnable() {

                @Override
                public void run() {
                    Looper.myLooper().quit();
                }

            });
        }
    }

    /**
     * Create a notification that shows, that we got a keep alive timeout
     */
    private void showKeepAliveDisconnectNotification() {
        // Set the result intent
        Intent resultIntent = new Intent(this, MainActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        resultIntent.putExtra(App.NOTIFICATION_ID, -1);

        // Create a TaskStack, so the app navigates correctly backwards
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingintent = stackBuilder
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_disconnect_keep_alive))
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(resultPendingintent)
            .build();
        mNotificationManager.notify(App.NOTIFY_ID, notification);
    }

    private void sendConnectMessageIfPossible(Intent intent) {
        if (intent.hasExtra(EXTRA_STRING_IP)) {
            final String ip = intent.getStringExtra(EXTRA_STRING_IP);
            final int port = intent.getIntExtra(EXTRA_INT_PORT, 0);
            final int auth = intent.getIntExtra(EXTRA_INT_AUTH, 0);

            Message msg = Message.obtain();
            msg.obj = ClementineMessageFactory
                    .buildConnectMessage(ip, port, auth, true, false);
            App.mClementineConnection.mHandler.sendMessage(msg);
        }
    }

}
