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

import androidx.fragment.app.Fragment;
import android.os.Bundle;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.ui.adapter.PlayerPageAdapter;
import de.qspool.clementineremote.ui.fragments.playerpages.ConnectionFragment;
import de.qspool.clementineremote.ui.fragments.playerpages.PlayerPageFragment;
import de.qspool.clementineremote.ui.fragments.playerpages.SongDetailFragment;
import de.qspool.clementineremote.ui.interfaces.BackPressHandleable;
import de.qspool.clementineremote.ui.interfaces.RemoteDataReceiver;
import de.qspool.clementineremote.ui.player.PlayerViews;
import de.qspool.clementineremote.ui.widgets.SlidingTabLayout;

public class PlayerFragment extends Fragment implements BackPressHandleable, RemoteDataReceiver {

    private ActionBar mActionBar;

    private SlidingTabLayout mTabs;

    private PlayerPageFragment mPlayerPageFragment;

    private SongDetailFragment mSongDetailFragment;

    private ConnectionFragment mConnectionFragment;

    private ViewPager myPager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        PlayerPageAdapter playerPageAdapter =
                new PlayerPageAdapter(getActivity(), getChildFragmentManager());
        playerPageAdapter.addFragment(mPlayerPageFragment);
        playerPageAdapter.addFragment(mSongDetailFragment);
        playerPageAdapter.addFragment(mConnectionFragment);
        myPager = (ViewPager) view.findViewById(R.id.player_pager);
        myPager.setAdapter(playerPageAdapter);
        myPager.setCurrentItem(0);

        PlayerViews.showControls((ComposeView) view.findViewById(R.id.player_controls));

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
                return ContextCompat.getColor(getActivity(), R.color.actionbar_dark);
            }
        });
        mTabs.setTextViewColor(ContextCompat.getColor(getActivity(), R.color.white));
        mTabs.setViewPager(myPager);
        mTabs.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPause() {
        super.onPause();
        mTabs.setVisibility(View.GONE);
    }

    @Override
    public void MessageFromClementine(ClementineMessage clementineMessage) {
        if (clementineMessage.getMessageType() == MsgType.CURRENT_METAINFO) {
            metadataChanged();
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

    @Override
    public boolean onBackPressed() {
        return false;
    }
}
