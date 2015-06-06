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

package de.qspool.clementineremote.ui.fragments;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import java.lang.reflect.Field;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.ui.adapter.PlayerPageAdapter;
import de.qspool.clementineremote.ui.fragments.playerpages.ConnectionFragment;
import de.qspool.clementineremote.ui.fragments.playerpages.PlayerPageFragment;
import de.qspool.clementineremote.ui.fragments.playerpages.SongDetailFragment;
import de.qspool.clementineremote.ui.interfaces.BackPressHandleable;
import de.qspool.clementineremote.ui.interfaces.RemoteDataReceiver;
import de.qspool.clementineremote.ui.widgets.SlidingTabLayout;

public class PlayerFragment extends Fragment implements BackPressHandleable, RemoteDataReceiver {

    private ImageButton mBtnNext;

    private ImageButton mBtnPrev;

    private ImageButton mBtnPlayPause;

    private ActionBar mActionBar;

    private SlidingTabLayout mTabs;

    private PlayerPageFragment mPlayerPageFragment;

    private SongDetailFragment mSongDetailFragment;

    private ConnectionFragment mConnectionFragment;

    private PlayerPageAdapter mPlayerPageAdapter;

    private ViewPager myPager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Get the actionbar
        mActionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        mActionBar.setTitle(R.string.player_playlist);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_player,
                container, false);

        mPlayerPageFragment = new PlayerPageFragment();

        mSongDetailFragment = new SongDetailFragment();

        mConnectionFragment = new ConnectionFragment();

        mPlayerPageAdapter = new PlayerPageAdapter(getActivity(), getChildFragmentManager());
        mPlayerPageAdapter.addFragment(mPlayerPageFragment);
        mPlayerPageAdapter.addFragment(mSongDetailFragment);
        mPlayerPageAdapter.addFragment(mConnectionFragment);
        myPager = (ViewPager) view.findViewById(R.id.player_pager);
        myPager.setAdapter(mPlayerPageAdapter);
        myPager.setCurrentItem(0);

        // Get the Views
        mBtnNext = (ImageButton) view.findViewById(R.id.btnNext);
        mBtnPrev = (ImageButton) view.findViewById(R.id.btnPrev);
        mBtnPlayPause = (ImageButton) view.findViewById(R.id.btnPlaypause);

        // Set the onclicklistener for the buttons
        mBtnNext.setOnClickListener(oclControl);
        mBtnPrev.setOnClickListener(oclControl);
        mBtnPlayPause.setOnClickListener(oclControl);
        mBtnPlayPause.setOnLongClickListener(olclControl);

        // Initialize interface
        stateChanged();
        metadataChanged();

        mTabs = (SlidingTabLayout) getActivity().findViewById(R.id.tabs);

        setHasOptionsMenu(true);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        myPager.setCurrentItem(0);

        mTabs.setDistributeEvenly(true);
        mTabs.setCustomTabColorizer(new SlidingTabLayout.TabColorizer() {
            @Override
            public int getIndicatorColor(int position) {
                return getResources().getColor(R.color.actionbar_dark);
            }
        });
        mTabs.setTextViewColor(getResources().getColor(R.color.white));
        mTabs.setViewPager(myPager);
        mTabs.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPause() {
        super.onPause();
        mTabs.setVisibility(View.GONE);
    }

    @Override
    public void onDetach() {
        super.onDetach();

        try {
            Field childFragmentManager = Fragment.class.getDeclaredField("mChildFragmentManager");
            childFragmentManager.setAccessible(true);
            childFragmentManager.set(this, null);

        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void MessageFromClementine(ClementineMessage clementineMessage) {
        switch (clementineMessage.getMessageType()) {
            case PLAY:
            case PAUSE:
            case STOP:
                stateChanged();
                break;
            case CURRENT_METAINFO:
                metadataChanged();
            default:
                break;
        }

        if (mPlayerPageFragment.isAdded()) {
            mPlayerPageFragment.MessageFromClementine(clementineMessage);
        }
        if (mSongDetailFragment.isAdded()) {
            mSongDetailFragment.MessageFromClementine(clementineMessage);
        }
        if (mConnectionFragment.isAdded()) {
            mConnectionFragment.MessageFromClementine(clementineMessage);
        }
    }

    private void metadataChanged() {
        // ActionBar shows the current playlist
        if (App.Clementine.getPlaylistManager().getActivePlaylist() != null) {
            mActionBar.setSubtitle(
                    App.Clementine.getPlaylistManager().getActivePlaylist().getName());
        }
    }

    private void stateChanged() {
        // display play / pause image
        if (App.Clementine.getState() == Clementine.State.PLAY) {
            mBtnPlayPause.setImageDrawable(
                    ContextCompat.getDrawable(getActivity(), R.drawable.ic_media_pause));
        } else {
            mBtnPlayPause.setImageDrawable(
                    ContextCompat.getDrawable(getActivity(), R.drawable.ic_media_play));
        }
    }

    private OnClickListener oclControl = new OnClickListener() {

        @Override
        public void onClick(View v) {
            Message msg = Message.obtain();

            switch (v.getId()) {
                case R.id.btnNext:
                    msg.obj = ClementineMessage.getMessage(MsgType.NEXT);
                    break;
                case R.id.btnPrev:
                    msg.obj = ClementineMessage.getMessage(MsgType.PREVIOUS);
                    break;
                case R.id.btnPlaypause:
                    msg.obj = ClementineMessage.getMessage(MsgType.PLAYPAUSE);
                    break;
                default:
                    break;
            }
            // Send the request to the thread
            if (msg.obj != null) {
                App.ClementineConnection.mHandler.sendMessage(msg);
            }
        }
    };

    private OnLongClickListener olclControl = new OnLongClickListener() {

        @Override
        public boolean onLongClick(View v) {
            boolean ret = false;
            Message msg = Message.obtain();

            switch (v.getId()) {
                case R.id.btnPlaypause:
                    Toast.makeText(getActivity(), R.string.player_stop_after_current,
                            Toast.LENGTH_SHORT).show();
                    msg.obj = ClementineMessage.getMessage(MsgType.STOP_AFTER);
                    ret = true;
                    break;
                default:
                    break;
            }

            App.ClementineConnection.mHandler.sendMessage(msg);
            return ret;
        }
    };

    @Override
    public boolean onBackPressed() {
        return false;
    }
}
