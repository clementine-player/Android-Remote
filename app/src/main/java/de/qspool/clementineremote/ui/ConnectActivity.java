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

package de.qspool.clementineremote.ui;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.jmdns.ServiceInfo;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.ClementineService;
import de.qspool.clementineremote.backend.RemoteRepository;
import de.qspool.clementineremote.backend.downloader.DownloadManager;
import de.qspool.clementineremote.backend.mdns.ClementineMDnsDiscovery;
import de.qspool.clementineremote.backend.mediasession.ClementineMediaSessionNotification;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ReasonDisconnect;
import de.qspool.clementineremote.ui.connect.ConnectActions;
import de.qspool.clementineremote.ui.connect.ConnectViewModel;
import de.qspool.clementineremote.ui.connect.ConnectViews;
import de.qspool.clementineremote.ui.connect.Server;
import de.qspool.clementineremote.ui.settings.ClementineSettings;
import de.qspool.clementineremote.utils.Utilities;

/**
 * The connect screen, drawn in Compose ({@code ConnectScreen}). This activity finds Clementines on
 * the network, connects to the one picked, and opens the player once connected.
 */
public class ConnectActivity extends AppCompatActivity implements ConnectActions {

    private final int ID_PLAYER_DIALOG = 1;

    private final int ID_SETTINGS = 2;

    private final int ID_PERMISSION_REQUEST = 3;

    public final static int RESULT_DISCONNECT = 1;

    public final static int RESULT_QUIT = 2;

    private SharedPreferences mSharedPref;

    private ConnectActivityHandler mHandler = new ConnectActivityHandler(this);

    private ConnectViewModel mState;

    private int mAuthCode = 0;

    private ClementineMDnsDiscovery mClementineMDns;

    private Intent mServiceIntent;

    private boolean doAutoConnect = true;

    private Set<String> mKnownIps;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_connectdialog);

        // Drawn wholly in Compose, which keeps clear of the system bars and the keyboard itself.
        // The status bar sits on Clementine's gradient, so its icons are light.
        androidx.activity.EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));

        mSharedPref = App.getPreferences();
        // A copy: the set the preferences return must not be changed.
        mKnownIps = new LinkedHashSet<>(mSharedPref
                .getStringSet(SharedPreferencesKeys.SP_KNOWN_IP, new LinkedHashSet<String>()));

        mState = new ViewModelProvider(this).get(ConnectViewModel.class);
        if (savedInstanceState == null) {
            mState.setHost(mSharedPref.getString(SharedPreferencesKeys.SP_KEY_IP, ""));
        }
        mState.setKnownHosts(mKnownIps);

        // Get the last auth code
        mAuthCode = mSharedPref.getInt(SharedPreferencesKeys.SP_LAST_AUTH_CODE, 0);

        ConnectViews.showConnect((ComposeView) findViewById(R.id.connect_content), mState, this);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Check if we are currently connected, then open the player dialog
        if (!mState.isConnecting()
                && App.ClementineConnection != null
                && App.ClementineConnection.isConnected()) {
            showPlayerDialog();
            return;
        }

        // mDNS discovery runs even when auto-connecting, so that if the saved address no longer
        // works, the Clementines on the network are there to pick from.
        mClementineMDns = new ClementineMDnsDiscovery(mHandler);
        mClementineMDns.discoverServices();

        // Check if Autoconnect is enabled
        if (mSharedPref.getBoolean(SharedPreferencesKeys.SP_KEY_AC, false) && doAutoConnect) {
            // Post delayed, so the service has time to start
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    connect();
                }
            }, 250);
        }
        doAutoConnect = true;

        // Remove still active notifications
        NotificationManager mNotificationManager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(ClementineMediaSessionNotification.NOTIFIFCATION_ID);
        mNotificationManager.cancel(DownloadManager.NOTIFICATION_ID_DOWNLOADS);
        mNotificationManager.cancel(DownloadManager.NOTIFICATION_ID_DOWNLOADS_FINISHED);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mClementineMDns != null) {
            mClementineMDns.stopServiceDiscovery();
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        // First time called? Show an info screen
        if (mSharedPref.getBoolean(SharedPreferencesKeys.SP_FIRST_CALL, true)) {
            mSharedPref.edit().putBoolean(SharedPreferencesKeys.SP_FIRST_CALL, false).apply();

            // Show the info screen
            showFirstTimeScreen();
        }

        final String[] missing = missingPermissions();
        if (missing.length > 0) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.permissions_required_title)
                    .setMessage(R.string.permissions_required_text)
                    .setNegativeButton(R.string.dialog_continue, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(ConnectActivity.this, missing,
                                    ID_PERMISSION_REQUEST);
                        }
                    })
                    .show();
        }
    }

    /**
     * The runtime permissions the app uses that have not been granted yet.
     */
    String[] missingPermissions() {
        List<String> wanted = new ArrayList<>();
        // Lowers Clementine's volume during calls.
        wanted.add(Manifest.permission.READ_PHONE_STATE);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // Downloads to folders outside the app's own; not needed from Android 10.
            wanted.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The player controls and download progress notifications.
            wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        List<String> missing = new ArrayList<>();
        for (String permission : wanted) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        return missing.toArray(new String[0]);
    }

    @Override
    public void onConnect() {
        connect();
    }

    @Override
    public void onServer(@NonNull Server server) {
        mState.setHost(server.getHost());
        mSharedPref.edit()
                .putString(SharedPreferencesKeys.SP_KEY_PORT, String.valueOf(server.getPort()))
                .apply();
        connect();
    }

    @Override
    public void onSearchAgain() {
        if (mClementineMDns != null) {
            mClementineMDns.stopServiceDiscovery();
        }
        mState.setServers(new ArrayList<Server>());
        mClementineMDns = new ClementineMDnsDiscovery(mHandler);
        mClementineMDns.discoverServices();
    }

    @Override
    public void onSettings() {
        startActivity(new Intent(this, ClementineSettings.class));
        doAutoConnect = false;
    }

    @Override
    public void onCancel() {
        RemoteRepository.send(ClementineMessage.getMessage(MsgType.DISCONNECT));
        mState.hideProgress();
    }

    /** Shows how far connecting has got. */
    void showProgress(@StringRes int progress) {
        mState.showProgress(progress);
    }

    /** Connecting has ended, whether connected or not. */
    void connectionEnded() {
        mState.hideProgress();
    }

    /**
     * Connect to clementine
     */
    private void connect() {
        // Do not connect if the activity has finished!
        if (this.isFinishing()) {
            return;
        }

        final String ip = mState.getHost().getValue();

        mKnownIps.add(ip);
        mState.setKnownHosts(mKnownIps);

        // Save the data
        SharedPreferences.Editor editor = mSharedPref.edit();
        editor.putString(SharedPreferencesKeys.SP_KEY_IP, ip);
        editor.putInt(SharedPreferencesKeys.SP_LAST_AUTH_CODE, mAuthCode);
        editor.putStringSet(SharedPreferencesKeys.SP_KNOWN_IP, mKnownIps);

        editor.apply();

        mState.showProgress(R.string.connectdialog_connecting);

        // Start the service so it won't be stopped on unbindService
        Intent serviceIntent = new Intent(this, ClementineService.class);
        startService(serviceIntent);

        bindService(serviceIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                ClementineService.ClementineServiceBinder clementineServiceBinder
                        = (ClementineService.ClementineServiceBinder) service;

                clementineServiceBinder.getClementineService().setUiHandler(mHandler);

                Intent connectIntent = new Intent(ConnectActivity.this, ClementineService.class);
                connectIntent.putExtra(ClementineService.SERVICE_ID,
                        ClementineService.SERVICE_START);
                connectIntent.putExtra(ClementineService.EXTRA_STRING_IP, ip);
                connectIntent.putExtra(ClementineService.EXTRA_INT_PORT, getPort());
                connectIntent.putExtra(ClementineService.EXTRA_INT_AUTH, mAuthCode);

                clementineServiceBinder.getClementineService().handleServiceAction(connectIntent);

                unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        }, Context.BIND_AUTO_CREATE);
    }

    private int getPort() {
        // Get the port to connect to
        int port;
        try {
            port = Integer.valueOf(
                    mSharedPref.getString(SharedPreferencesKeys.SP_KEY_PORT,
                            String.valueOf(Clementine.DefaultPort)));
        } catch (NumberFormatException e) {
            port = Clementine.DefaultPort;
        }

        return port;
    }

    /**
     * Show the user the dialog to enter the auth code
     */
    void showAuthCodePromt() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.input_auth_code)
                .setView(input)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        // Set the listener after show() so an invalid code keeps the dialog open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mAuthCode = Integer.parseInt(input.getText().toString());
                    dialog.dismiss();
                    connect();
                } catch (NumberFormatException e) {
                    Toast.makeText(ConnectActivity.this, R.string.invalid_code,
                            Toast.LENGTH_SHORT)
                            .show();
                }
            }
        });
    }

    /**
     * Show the user the first time called dialog
     */
    private void showFirstTimeScreen() {
        Utilities.ShowMessageDialog(this,
                getString(R.string.first_time_title),
                getString(R.string.first_time_text, getString(R.string.clementine_version)),
                true);
    }

    /**
     * We connected to clementine successfully. Now open other view
     */
    void showPlayerDialog() {
        if (mClementineMDns != null) {
            mClementineMDns.stopServiceDiscovery();
        }

        // Start the player dialog
        Intent playerDialog = new Intent(this, MainActivity.class);
        playerDialog.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivityForResult(playerDialog, ID_PLAYER_DIALOG);
    }

    /**
     * We couldn't connect to clementine. Inform the user
     */
    void noConnection() {
        // Do not display dialog if the activity has finished!
        if (this.isFinishing()) {
            return;
        }

        // Check if we have not a local ip
        WifiManager wifiManager =
                (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();

        // Get the current wifi state
        if (!Utilities.onWifi()) {
            Utilities.ShowMessageDialog(this, R.string.connectdialog_error, R.string.wifi_disabled);
        } else if (!Utilities.ToInetAddress(ip).isSiteLocalAddress()) {
            Utilities.ShowMessageDialog(this, R.string.connectdialog_error, R.string.no_private_ip);
        } else {
            Utilities.ShowMessageDialog(this,
                    getString(R.string.connectdialog_error),
                    getString(R.string.check_ip, getString(R.string.clementine_version)),
                    false);
        }
    }

    /**
     * We have an old Proto version. User has to update Clementine
     */
    void oldProtoVersion() {
        String title = getString(R.string.error_versions);
        String message = getString(R.string.old_proto, getString(R.string.clementine_version));
        Utilities.ShowMessageDialog(this, title, message, false);
    }

    /**
     * Clementine closed the connection
     *
     * @param clementineMessage The object to work with
     */
    void disconnected(ClementineMessage clementineMessage) {
        // Restart the background service
        mServiceIntent = new Intent(this, ClementineService.class);
        mServiceIntent.putExtra(ClementineService.SERVICE_ID, ClementineService.SERVICE_START);
        startService(mServiceIntent);

        if (!clementineMessage.isErrorMessage()) {
            if (clementineMessage.getMessage().getResponseDisconnect()
                    .getReasonDisconnect() == ReasonDisconnect.Wrong_Auth_Code ||
                    clementineMessage.getMessage().getResponseDisconnect()
                            .getReasonDisconnect() == ReasonDisconnect.Not_Authenticated ) {
                showAuthCodePromt();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ID_PLAYER_DIALOG) {
            if (resultCode == Activity.RESULT_CANCELED || resultCode == RESULT_QUIT) {
                finish();
            } else {
                doAutoConnect = false;
            }
        } else if (requestCode == ID_SETTINGS) {
            doAutoConnect = false;
        } else if (requestCode == ID_PERMISSION_REQUEST) {
            return;
        }
    }

    /**
     * The Clementines found on the network changed.
     */
    void serviceFound() {
        List<Server> servers = new ArrayList<>();
        for (ServiceInfo service : mClementineMDns.getServices()) {
            Inet4Address[] addresses = service.getInet4Addresses();
            if (addresses.length > 0) {
                servers.add(new Server(service.getName(), addresses[0].getHostAddress(),
                        service.getPort()));
            }
        }
        mState.setServers(servers);
    }
}
