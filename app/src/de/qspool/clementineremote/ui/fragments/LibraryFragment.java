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

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.ProgressDialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Message;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedList;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.ClementineLibraryDownloader;
import de.qspool.clementineremote.backend.elements.DownloaderResult;
import de.qspool.clementineremote.backend.elements.DownloaderResult.DownloadResult;
import de.qspool.clementineremote.backend.listener.OnLibraryDownloadListener;
import de.qspool.clementineremote.backend.listener.OnLibrarySelectFinishedListener;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.player.MyLibrary;
import de.qspool.clementineremote.backend.player.MyLibraryItem;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.ui.adapter.LibraryAdapter;
import de.qspool.clementineremote.utils.Utilities;

public class LibraryFragment extends AbstractDrawerFragment implements
        OnLibrarySelectFinishedListener {

    private final String TAG = "LibraryFragment";

    private ActionBar mActionBar;

    private ListView mList;

    private LinkedList<LibraryAdapter> mAdapters = new LinkedList<LibraryAdapter>();

    private MyLibrary mLibrary;

    private View mEmptyLibrary;

    private TextView mLibraryEmptyText;

    private ProgressDialog mProgressDialog;

    private String mLastFilter = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the actionbar
        mActionBar = getActivity().getActionBar();
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Check if we are still connected
        if (App.mClementineConnection == null || App.mClementine == null
                || !App.mClementineConnection.isConnected()) {
            return;
        }

        setActionBarTitle();
        if (App.libraryDownloader != null) {
            createDownloadProgressDialog();
            App.libraryDownloader.addOnLibraryDownloadListener(mOnLibraryDownloadListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (App.libraryDownloader != null) {
            App.libraryDownloader.removeOnLibraryDownloadListener(mOnLibraryDownloadListener);
            mProgressDialog.dismiss();
        }
    }

    @SuppressLint({"InlinedApi", "NewApi"})
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_library, container,
                false);

        mList = (ListView) view.findViewById(R.id.library);
        mEmptyLibrary = view.findViewById(R.id.library_empty);

        mLibraryEmptyText = (TextView) mEmptyLibrary.findViewById(R.id.library_empty_txt);

        mList.setOnItemClickListener(oiclLibraryClick);
        mList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mList.setMultiChoiceModeListener(new MultiChoiceModeListener() {
            @Override
            public boolean onActionItemClicked(ActionMode mode,
                    android.view.MenuItem item) {
                SparseBooleanArray checkedPositions = mList.getCheckedItemPositions();

                switch (item.getItemId()) {
                    case R.id.library_context_add:
                        for (int i = 0; i < checkedPositions.size(); ++i) {
                            int position = checkedPositions.keyAt(i);
                            if (checkedPositions.valueAt(i)) {
                                MyLibraryItem libraryItem = mAdapters.getLast()
                                        .getItem(position);
                                addSongsToPlaylist(libraryItem);
                            }
                        }
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
                inflater.inflate(R.menu.library_context_menu, menu);
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

        // Create the adapter
        mLibrary = new MyLibrary(getActivity());
        mLibrary.removeDatabaseIfFromOtherClementine();
        if (App.libraryDownloader == null && mLibrary.databaseExists()) {
            mLibrary.openDatabase();
            LibraryAdapter a = new LibraryAdapter(getActivity(), mLibrary.getArtists(), mLibrary,
                    MyLibrary.LVL_ARTIST);
            mAdapters.add(a);
        }

        showList();

        mActionBar.setTitle("");
        mActionBar.setSubtitle("/");

        setHasOptionsMenu(true);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (mLibrary != null) {
            mLibrary.closeDatabase();
            mAdapters.clear();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.library_menu_refresh:
                mAdapters.clear();
                showList();

                App.libraryDownloader = new ClementineLibraryDownloader(getActivity());
                App.libraryDownloader
                        .addOnLibraryDownloadListener(mOnLibraryDownloadListener);
                App.libraryDownloader.startDownload(ClementineMessage
                        .getMessage(MsgType.GET_LIBRARY));

                createDownloadProgressDialog();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    private OnLibraryDownloadListener mOnLibraryDownloadListener = new OnLibraryDownloadListener() {

        @Override
        public void OnLibraryDownloadFinished(DownloaderResult result) {
            mProgressDialog.dismiss();
            App.libraryDownloader = null;

            if (result.getResult() == DownloadResult.SUCCESSFUL) {
                if (mLibrary != null) {
                    mLibrary.closeDatabase();
                }
                mLibrary = new MyLibrary(getActivity());
                mLibrary.openDatabase();
                LibraryAdapter a = new LibraryAdapter(getActivity(), mLibrary.getArtists(),
                        mLibrary, MyLibrary.LVL_ARTIST);
                mAdapters.add(a);
                showList();
            } else {
                Utilities.ShowMessageDialog(getActivity(), R.string.library_download_error,
                        result.getMessageId());
            }
        }

        @Override
        public void OnProgressUpdate(long progress, int total) {
            mProgressDialog.setProgress((int) progress);
            mProgressDialog.setMax(total);
        }

        @Override
        public void OnOptimizeLibrary() {
            mProgressDialog.dismiss();

            mProgressDialog = new ProgressDialog(getActivity());
            mProgressDialog.setTitle(R.string.library_please_wait);
            mProgressDialog.setMessage(getText(R.string.library_optimize));
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }
    };

    private void createDownloadProgressDialog() {
        mProgressDialog = new ProgressDialog(getActivity());
        mProgressDialog.setTitle(R.string.library_please_wait);
        mProgressDialog.setMessage(getText(R.string.library_download));
        mProgressDialog.setMax(0);
        mProgressDialog.setProgress(0);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(false);

        mProgressDialog.show();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.library_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // Create a listener for search change
        SearchView searchView = (SearchView) menu.findItem(
                R.id.library_menu_search).getActionView();

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
                if (!mAdapters.isEmpty()) {
                    mAdapters.getLast().getFilter().filter(newText);
                    mLastFilter = newText;

                    mLibraryEmptyText.setText(R.string.library_no_search_results);
                }
                return true;
            }

            @Override
            public boolean onQueryTextSubmit(String query) {
                // Do something
                if (!mAdapters.isEmpty()) {
                    mAdapters.getLast().getFilter().filter(query);
                    mLastFilter = query;
                }

                return true;
            }
        };
        searchView.setOnQueryTextListener(queryTextListener);
        searchView.setQueryHint(getString(R.string.playlist_search_hint));

        super.onPrepareOptionsMenu(menu);
    }

    private boolean addSongsToPlaylist(MyLibraryItem libraryItem) {
        switch (libraryItem.getLevel()) {
            case MyLibrary.LVL_ARTIST:
                MyLibrary addArtist = new MyLibrary(getActivity());
                addArtist.addOnLibrarySelectFinishedListener(LibraryFragment.this);
                addArtist.getAllTitlesFromArtistAsync(libraryItem.getArtist());
                return true;
            case MyLibrary.LVL_ALBUM:
                MyLibrary addAlbums = new MyLibrary(getActivity());
                addAlbums.addOnLibrarySelectFinishedListener(LibraryFragment.this);
                addAlbums.getTitlesAsync(libraryItem.getArtist(), libraryItem.getAlbum());
                return true;
            case MyLibrary.LVL_TITLE:
                Message msg = Message.obtain();
                LinkedList<String> urls = new LinkedList<String>();
                urls.add(libraryItem.getUrl());
                msg.obj = ClementineMessageFactory.buildInsertUrl(
                        App.mClementine.getPlaylistManager().getActivePlaylistId(), urls);
                App.mClementineConnection.mHandler.sendMessage(msg);
                return false;
            default:
                return false;
        }
    }

    private void setActionBarTitle() {
        MySong currentSong = App.mClementine.getCurrentSong();
        if (currentSong == null) {
            mActionBar.setTitle(getString(R.string.player_nosong));
        } else {
            mActionBar.setTitle(currentSong.getArtist() + " / " + currentSong.getTitle());
        }
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mList.setFastScrollEnabled(true);
        mList.setTextFilterEnabled(true);
        mList.setSelector(new ColorDrawable(android.R.color.transparent));
        mList.setOnItemClickListener(oiclLibraryClick);

        mList.setEmptyView(mEmptyLibrary);
    }

    @Override
    public void MessageFromClementine(ClementineMessage clementineMessage) {
        switch (clementineMessage.getMessageType()) {
            case CURRENT_METAINFO:
                setActionBarTitle();
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onBackPressed() {
        // When we have only one item left, just use the normal back behavior
        if (mAdapters.size() <= 1) {
            return false;
        }

        // Remove the last element and show the new list
        mAdapters.removeLast();
        showList();

        return true;
    }

    /**
     * Show the last element in the list of adapters
     */
    private void showList() {
        if (mAdapters.isEmpty() || mAdapters.getLast().isEmpty()) {
            mList.setEmptyView(mEmptyLibrary);
        } else {
            LibraryAdapter adapter = mAdapters.getLast();
            adapter.getFilter().filter(mLastFilter);
            mList.setAdapter(adapter);
            if (adapter.isEmpty() || adapter.getCount() == 0) {
                buildSubActionBar("", "");
            } else {
                MyLibraryItem item = adapter.getItem(0);
                switch (item.getLevel()) {
                    case MyLibrary.LVL_ARTIST:
                        buildSubActionBar("", "");
                        break;
                    case MyLibrary.LVL_ALBUM:
                        buildSubActionBar(item.getArtist(), "");
                        break;
                    default:
                        buildSubActionBar(item.getArtist(), item.getAlbum());
                        break;
                }
            }
        }
    }

    private void buildSubActionBar(String artist, String album) {
        StringBuilder sb = new StringBuilder();

        sb.append("/ ");
        sb.append(artist);
        if (album.length() > 0) {
            sb.append(" / ");
            sb.append(album);
        }

        mActionBar.setSubtitle(sb.toString());
    }

    private void addSongsToPlaylist(LinkedList<MyLibraryItem> l) {
        Message msg = Message.obtain();
        LinkedList<String> urls = new LinkedList<String>();
        for (MyLibraryItem item : l) {
            urls.add(item.getUrl());
        }

        msg.obj = ClementineMessageFactory.buildInsertUrl(
                App.mClementine.getPlaylistManager().getActivePlaylistId(), urls);

        App.mClementineConnection.mHandler.sendMessage(msg);

        Toast.makeText(getActivity(),
                String.format(getString(R.string.library_songs_added), urls.size()),
                Toast.LENGTH_SHORT).show();
    }

    private OnItemClickListener oiclLibraryClick = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {
            MyLibraryItem item = mAdapters.getLast().getItem(position);

            switch (item.getLevel()) {
                case MyLibrary.LVL_ARTIST:
                    LibraryAdapter album = new LibraryAdapter(getActivity(),
                            mLibrary.getAlbums(item.getArtist()), mLibrary, MyLibrary.LVL_ALBUM);
                    mAdapters.add(album);
                    showList();
                    break;
                case MyLibrary.LVL_ALBUM:
                    LibraryAdapter title = new LibraryAdapter(getActivity(),
                            mLibrary.getTitles(item.getArtist(), item.getAlbum()), mLibrary,
                            MyLibrary.LVL_TITLE);
                    mAdapters.add(title);
                    showList();
                    break;
                case MyLibrary.LVL_TITLE:
                    Message msg = Message.obtain();
                    LinkedList<String> urls = new LinkedList<String>();
                    urls.add(item.getUrl());
                    msg.obj = ClementineMessageFactory.buildInsertUrl(
                            App.mClementine.getPlaylistManager().getActivePlaylistId(), urls);
                    App.mClementineConnection.mHandler.sendMessage(msg);

                    Toast.makeText(getActivity(),
                            String.format(getString(R.string.library_songs_added), 1),
                            Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void OnLibrarySelectFinished(LinkedList<MyLibraryItem> l) {
        addSongsToPlaylist(l);
    }
}
