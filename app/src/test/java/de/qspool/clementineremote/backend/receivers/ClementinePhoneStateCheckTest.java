package de.qspool.clementineremote.backend.receivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.os.Message;
import android.telephony.TelephonyManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.List;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.ClementineConnectionHandler;
import de.qspool.clementineremote.backend.ClementinePlayerConnection;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;

@RunWith(RobolectricTestRunner.class)
public class ClementinePhoneStateCheckTest {

    private final List<ClementineMessage> mSent = new ArrayList<>();
    private ClementinePlayerConnection mPreviousConnection;
    private Clementine mPreviousClementine;
    private boolean mConnected;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_PHONE_STATE);
        App.getPreferences().edit()
                .clear()
                .putBoolean(SharedPreferencesKeys.SP_LOWER_VOLUME, true)
                .putString(SharedPreferencesKeys.SP_CALL_VOLUME, "20")
                .commit();

        mPreviousConnection = App.ClementineConnection;
        mPreviousClementine = App.Clementine;
        App.Clementine = new Clementine();
        App.Clementine.setVolume(80);
        App.Clementine.setState(Clementine.State.PLAY);
        mConnected = true;

        ClementinePlayerConnection connection = new ClementinePlayerConnection() {
            @Override
            public boolean isConnected() {
                return mConnected;
            }
        };
        connection.mHandler = new ClementineConnectionHandler(connection) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.obj instanceof ClementineMessage) {
                    mSent.add((ClementineMessage) msg.obj);
                }
            }
        };
        App.ClementineConnection = connection;
    }

    @After
    public void tearDown() {
        App.ClementineConnection = mPreviousConnection;
        App.Clementine = mPreviousClementine;
    }

    private void dispatchPhoneState(String state) {
        Intent intent = new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_STATE, state);
        new ClementinePhoneStateCheck().onReceive(RuntimeEnvironment.getApplication(), intent);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void idleWhileDisconnectedClearsSavedCallStateForNextCall() {
        dispatchPhoneState(TelephonyManager.EXTRA_STATE_RINGING);
        assertEquals(1, mSent.size());
        assertEquals(MsgType.SET_VOLUME, mSent.get(0).getMessageType());
        assertEquals(20, mSent.get(0).getMessage().getRequestSetVolume().getVolume());

        // Disconnect before the call finishes: IDLE arrives offline.
        mConnected = false;
        dispatchPhoneState(TelephonyManager.EXTRA_STATE_IDLE);
        assertEquals(1, mSent.size());

        // Reconnect for a subsequent call: volume lowering must still work and restore new volume.
        mSent.clear();
        App.Clementine.setVolume(65);
        mConnected = true;
        dispatchPhoneState(TelephonyManager.EXTRA_STATE_RINGING);
        assertEquals(1, mSent.size());
        assertEquals(20, mSent.get(0).getMessage().getRequestSetVolume().getVolume());

        dispatchPhoneState(TelephonyManager.EXTRA_STATE_IDLE);
        assertEquals(2, mSent.size());
        assertEquals(65, mSent.get(1).getMessage().getRequestSetVolume().getVolume());
    }
}
