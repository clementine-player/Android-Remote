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

import android.app.ActionBar;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Message;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.downloader.DownloadManager;
import de.qspool.clementineremote.backend.listener.OnPlaylistReceivedListener;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem;
import de.qspool.clementineremote.backend.player.MyPlaylist;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.backend.player.PlaylistManager;
import de.qspool.clementineremote.ui.adapter.PlaylistSongAdapter;
import de.qspool.clementineremote.ui.interfaces.BackPressHandleable;
import de.qspool.clementineremote.ui.interfaces.RemoteDataReceiver;

public class PlaylistFragment extends Fragment implements BackPressHandleable, RemoteDataReceiver {

    private PlaylistSongAdapter mAdapter;

    private ProgressDialog mProgressDialog;

    private ActionBar mActionBar;

    private ListView mList;

    private View mEmptyPlaylist;

    private PlaylistManager mPlaylistManager;

    private OnPlaylistReceivedListener mPlaylistListener;

    private LinkedList<MyPlaylist> mPlaylists = new LinkedList<>();

    private String mFilterText;

    private boolean mUpdateTrackPositionOnNewTrack = false;

    private int mSelectionOffset;

    public PlaylistFragment() {
        mFilterText = "";
        mSelectionOffset = 3;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the actionbar
        mActionBar = getActivity().getActionBar();
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        setHasOptionsMenu(true);

        mPlaylistManager = App.Clementine.getPlaylistManager();
        mPlaylistListener = new OnPlaylistReceivedListener() {
            @Override
            public void onPlaylistSongsReceived(final MyPlaylist p) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mProgressDialog != null) {
                            mProgressDialog.setProgress(mProgressDialog.getProgress() + 1);
                            mProgressDialog.setMessage(p.getName());
                        }

                        updateSongList();
                    }
                });
            }

            @Override
            public void onPlaylistReceived(final MyPlaylist p) {
            }

            @Override
            public void onAllRequestedPlaylistSongsReceived() {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mProgressDialog != null && mProgressDialog.isShowing()) {
                            mPlaylists = mPlaylistManager.getAllPlaylists();

                            mProgressDialog.dismiss();
                            getActivity().invalidateOptionsMenu();

                            mActionBar.setSelectedNavigationItem(
                                    mPlaylists.indexOf(mPlaylistManager.getActivePlaylist()));
                        }
                    }
                });
            }

            @Override
            public void onAllPlaylistsReceived() {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mPlaylists = mPlaylistManager.getAllPlaylists();
                        updatePlaylistSpinner();
                        RequestPlaylistSongs();
                    }
                });
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        // Check if we are still connected
        if (App.ClementineConnection == null
                || App.Clementine == null
                || !App.ClementineConnection.isConnected()) {
            return;
        }

        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

        mPlaylistManager.addOnPlaylistReceivedListener(mPlaylistListener);
        mPlaylists = mPlaylistManager.getAllPlaylists();
        updatePlaylistSpinner();

        RequestPlaylistSongs();

        // Get the position of the current track if we have one
        if (App.Clementine.getCurrentSong() != null) {
            updateViewPosition();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        mPlaylistManager.removeOnPlaylistReceivedListener(mPlaylistListener);
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlist,
                container, false);

        mPlaylists = mPlaylistManager.getAllPlaylists();

        mList = (ListView) view.findViewById(R.id.songs);
        mEmptyPlaylist = view.findViewById(R.id.playlist_empty);

        // update spinner
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        updatePlaylistSpinner();

        // Create the adapter
        mAdapter = new PlaylistSongAdapter(getActivity(), R.layout.item_playlist,
                getSelectedPlaylistSongs());

        mList.setOnItemClickListener(oiclSong);
        mList.setAdapter(mAdapter);

        mList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mList.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public boolean onActionItemClicked(ActionMode mode,
                    android.view.MenuItem item) {
                SparseBooleanArray checkedPositions = mList.getCheckedItemPositions();

                switch (item.getItemId()) {
                    case R.id.playlist_context_play:
                        for (int i = 0; i < checkedPositions.size(); ++i) {
                            int position = checkedPositions.keyAt(i);
                            if (checkedPositions.valueAt(i)) {
                                MySong s = getSelectedPlaylistSongs().get(position);
                                playSong(s);
                                break;
                            }
                        }
                        mode.finish();
                        return true;
                    case R.id.playlist_context_remove:
                        LinkedList<MySong> songs = new LinkedList<>();
                        for (int i = 0; i < checkedPositions.size(); ++i) {
                            int position = checkedPositions.keyAt(i);
                            if (checkedPositions.valueAt(i)) {
                                MySong s = getSelectedPlaylistSongs().get(position);
                                songs.add(s);
                            }
                        }
                        Message msg = Message.obtain();
                        msg.obj = ClementineMessageFactory
                                .buildRemoveMultipleSongsFromPlaylist(getPlaylistId(),
                                        songs);
                        App.ClementineConnection.mHandler.sendMessage(msg);
                        mode.finish();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode,
                    android.view.Menu menu) {
                android.view.MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.playlist_context_menu, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode,
                    android.view.Menu menu) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }

            @Override
            public void onItemCheckedStateChanged(ActionMode mode,
                    int position, long id, boolean checked) {
            }
        });

        // Filter the results
        mAdapter.getFilter().filter(mFilterText);

        mActionBar.setTitle("");
        mActionBar.setSubtitle("");

        return view;
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mList.setFastScrollEnabled(true);
        mList.setTextFilterEnabled(true);
        mList.setSelector(new ColorDrawable(android.R.color.transparent));
        mList.setDivider(null);
        mList.setDividerHeight(0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.download_playlist:
                DownloadManager.getInstance().addJob(ClementineMessageFactory
                        .buildDownloadSongsMessage(getPlaylistId(), DownloadItem.APlaylist));
                return true;
            case R.id.clear_playlist:
                mPlaylistManager.clearPlaylist(getPlaylistId());
                updateSongList();
                return true;
            case R.id.close_playlist:
                Message msg = Message.obtain();
                msg.obj = ClementineMessageFactory.buildClosePlaylist(getPlaylistId());
                App.ClementineConnection.mHandler.sendMessage(msg);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.playlist_menu, menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        android.view.MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.playlist_context_menu, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // Create a listener for search change
        SearchView searchView = (SearchView) menu.findItem(R.id.playlist_menu_search)
                .getActionView();

        int searchPlateId = searchView.getContext().getResources().getIdentifier("android:id/search_plate", null, null);
        // Getting the 'search_plate' LinearLayout.
        View searchPlate = searchView.findViewById(searchPlateId);
        // Setting background of 'search_plate' to earlier defined drawable.
        searchPlate.setBackgroundResource(R.drawable.texfield_searchview_holo);

        final SearchView.OnQueryTextListener queryTextListener
                = new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String newText) {
                // Set the filter text as the fragments might not yet
                // created. Only the left and right fragment from the
                // currently active is created (onCreate() called).
                // Therefore the other adapters are not yet created,
                // onCreate filters for this string given in setFilterText()
                setFilterText(newText);
                if (getAdapter() != null) {
                    getAdapter().getFilter().filter(newText);
                }
                return true;
            }

            @Override
            public boolean onQueryTextSubmit(String query) {
                // Do something
                setFilterText(query);
                if (getAdapter() != null) {
                    getAdapter().getFilter().filter(query);
                }

                return true;
            }
        };
        searchView.setOnQueryTextListener(queryTextListener);
        searchView.setQueryHint(getString(R.string.playlist_search_hint));

        super.onPrepareOptionsMenu(menu);
    }

    /**
     * Update the underlying data. It reloads the current playlist songs from the Clementine
     * object.
     */
    public void updateSongList() {
        // Check if we should update the current view position
        mAdapter = new PlaylistSongAdapter(getActivity(), R.layout.item_playlist,
                getSelectedPlaylistSongs());
        mList.setAdapter(mAdapter);

        // We have to post notifyDataSetChanged() here, so fast scroll is set correctly.
        // Without it, the fast scroll cannot get any child views as the adapter is not yet fully
        // attached to the view and getChildCount() returns 0. Therefore, fast scroll won't
        // be enabled.
        // notifyDataSetChanged() forces the listview to recheck the fast scroll preconditions.
        mList.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });

        updateViewPosition();

        if (mPlaylists.isEmpty()) {
            mList.setEmptyView(mEmptyPlaylist);
        }

    }

    /**
     * Set the text to filter
     *
     * @param filterText String, which results are filtered by
     */
    public void setFilterText(String filterText) {
        mFilterText = filterText;
    }

    /**
     * Get the song adapter
     *
     * @return The CustomSongAdapter
     */
    public PlaylistSongAdapter getAdapter() {
        return mAdapter;
    }

    private OnItemClickListener oiclSong = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            playSong(mAdapter.getItem(position));
        }
    };

    private void playSong(MySong song) {
        Message msg = Message.obtain();
        msg.obj = ClementineMessageFactory.buildRequestChangeSong(song.getIndex(), getPlaylistId());
        App.ClementineConnection.mHandler.sendMessage(msg);

        mPlaylistManager.setActivePlaylist(getPlaylistId());
    }


    /**
     * Set the selection to the currently played item
     */
    private void updateViewPosition() {
        if (App.Clementine.getCurrentSong() != null
                && mPlaylistManager.getActivePlaylistId() == getPlaylistId()) {
            int pos = App.Clementine.getCurrentSong().getIndex();
            mList.setSelection(pos - mSelectionOffset);
        }
    }

    public boolean isUpdateTrackPositionOnNewTrack() {
        return mUpdateTrackPositionOnNewTrack;
    }

    public void setUpdateTrackPositionOnNewTrack(
            boolean updateTrackPositionOnNewTrack, int offset) {
        this.mUpdateTrackPositionOnNewTrack = updateTrackPositionOnNewTrack;
        mSelectionOffset = offset;
    }

    @Override
    public void MessageFromClementine(ClementineMessage clementineMessage) {
        switch (clementineMessage.getMessageType()) {
            case CURRENT_METAINFO:
                updateSongList();
                break;
            default:
                break;
        }
    }

    /**
     * Sends a request to Clementine to send all songs in all active playlists.
     */
    public void RequestPlaylistSongs() {
        // If a progress is showing, do not show again!
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            return;
        }

        // Open it directly only when we got all playlists
        int requests = mPlaylistManager.requestAllPlaylistSongs();
        if (requests > 0) {
            // Start a Progressbar
            mProgressDialog = new ProgressDialog(getActivity());
            mProgressDialog.setMax(requests);
            mProgressDialog.setCancelable(true);
            mProgressDialog.setTitle(R.string.player_download_playlists);
            mProgressDialog.setMessage(getString(R.string.playlist_loading));
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.show();
        } else {
            mActionBar.setSelectedNavigationItem(
                    mPlaylists.indexOf(mPlaylistManager.getActivePlaylist()));
        }
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }

    private void updatePlaylistSpinner() {
        List<CharSequence> arrayList = new ArrayList<CharSequence>();
        for (int i = 0; i < mPlaylists.size(); i++) {
            arrayList.add(mPlaylists.get(i).getName());
        }

        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getActivity(),
                android.R.layout.simple_spinner_item, arrayList);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mActionBar.setListNavigationCallbacks(adapter, new ActionBar.OnNavigationListener() {

            @Override
            public boolean onNavigationItemSelected(int itemPosition, long itemId) {
                updateSongList();
                return true;
            }
        });
    }

    private int getPlaylistId() {
        return mPlaylists.get(getSelectedPlaylistPosition()).getId();
    }

    private LinkedList<MySong> getSelectedPlaylistSongs() {
        return mPlaylists.get(getSelectedPlaylistPosition()).getPlaylistSongs();
    }

    private int getSelectedPlaylistPosition() {
        int pos = mActionBar.getSelectedNavigationIndex();
        if (pos == Spinner.INVALID_POSITION || pos >= mPlaylists.size()) {
            pos = mPlaylists.indexOf(mPlaylistManager.getActivePlaylist());
            mActionBar.setSelectedNavigationItem(pos);
        }
        return pos;
    }
}
