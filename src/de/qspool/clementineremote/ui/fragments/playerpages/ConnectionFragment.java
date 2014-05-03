/* T
his file is part of the Android Clementine Remote.
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

package de.qspool.clementineremote.ui.fragments.playerpages;

import android.content.SharedPreferences;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.ui.fragments.AbstractDrawerFragment;
import de.qspool.clementineremote.utils.Utilities;

public class ConnectionFragment extends AbstractDrawerFragment {

    private TextView tv_ip;

    private TextView tv_version;

    private TextView tv_traffic;

    private SeekBar sb_volume;

    private SharedPreferences mSharedPref;

    private Timer mUpdateTimer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the shared preferences
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.player_connection_page,
                container, false);

        tv_ip = (TextView) view.findViewById(R.id.cn_ip);
        tv_version = (TextView) view.findViewById(R.id.cn_version);
        tv_traffic = (TextView) view.findViewById(R.id.cn_traffic);

        updateTraffic();

        tv_ip.setText(mSharedPref.getString(App.SP_KEY_IP, "") + ":" + mSharedPref
                .getString(App.SP_KEY_PORT, ""));
        tv_version.setText(App.mClementine.getVersion());

        sb_volume = (SeekBar) view.findViewById(R.id.cn_volume);
        sb_volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    Message msg = Message.obtain();
                    msg.obj = ClementineMessageFactory.buildVolumeMessage(progress);
                    App.mClementineConnection.mHandler.sendMessage(msg);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        sb_volume.setProgress(App.mClementine.getVolume());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        mUpdateTimer = new Timer();
        mUpdateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateTraffic();
                        }
                    });
                }
            }
        }, 250, 250);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mUpdateTimer != null) {
            mUpdateTimer.cancel();
        }
    }

    private void updateTraffic() {
        int uid = getActivity().getApplicationInfo().uid;

        if (TrafficStats.getUidRxBytes(uid) == TrafficStats.UNSUPPORTED) {
            tv_traffic.setText(R.string.connection_traffic_unsupported);
        } else {
            String tx = Utilities.humanReadableBytes(
                    TrafficStats.getUidTxBytes(uid) - App.mClementineConnection.getStartTx(), true);
            String rx = Utilities.humanReadableBytes(
                    TrafficStats.getUidRxBytes(uid) - App.mClementineConnection.getStartRx(), true);

            tv_traffic.setText(tx + " / " + rx);
        }
    }

    @Override
    public void MessageFromClementine(ClementineMessage clementineMessage) {
        switch (clementineMessage.getMessageType()) {
            case SET_VOLUME:
                sb_volume.setProgress(App.mClementine.getVolume());
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }
}
