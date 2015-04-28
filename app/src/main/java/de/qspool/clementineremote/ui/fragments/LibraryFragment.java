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
import android.app.Fragment;
import android.app.ProgressDialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedList;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.ClementineLibraryDownloader;
import de.qspool.clementineremote.backend.downloader.DownloadManager;
import de.qspool.clementineremote.backend.elements.DownloaderResult;
import de.qspool.clementineremote.backend.elements.DownloaderResult.DownloadResult;
import de.qspool.clementineremote.backend.library.LibraryDatabaseHelper;
import de.qspool.clementineremote.backend.library.LibraryGroup;
import de.qspool.clementineremote.backend.library.LibrarySelectItem;
import de.qspool.clementineremote.backend.listener.OnLibraryDownloadListener;
import de.qspool.clementineremote.backend.listener.OnLibrarySelectFinishedListener;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.ui.adapter.LibraryAdapter;
import de.qspool.clementineremote.ui.interfaces.BackPressHandleable;
import de.qspool.clementineremote.ui.interfaces.RemoteDataReceiver;
import de.qspool.clementineremote.utils.Utilities;

public class LibraryFragment extends Fragment implements BackPressHandleable, RemoteDataReceiver,
        SwipeRefreshLayout.OnRefreshListener {

    private ActionBar mActionBar;

    private SwipeRefreshLayout mSwipeRefreshLayout;

    private SwipeRefreshLayout mEmptyLibrary;

    private ListView mList;

    private LinkedList<LibraryAdapter> mAdapters = new LinkedList<LibraryAdapter>();

    private TextView mLibraryEmptyText;

    private ProgressDialog mProgressDialog;

    private String mLastFilter = "";

    private ClementineLibraryDownloader mClementineLibraryDownloader;

    private int mLibraryQueriesDone;

    private int mLibraryLevels;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the actionbar
        mActionBar = ((ActionBarActivity) getActivity()).getSupportActionBar();
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Check if we are still connected
        if (App.ClementineConnection == null || App.Clementine == null
                || !App.ClementineConnection.isConnected()) {
            return;
        }

        setActionBarTitle();

        if (mClementineLibraryDownloader != null) {
            createDownloadProgressDialog();
            mClementineLibraryDownloader.addOnLibraryDownloadListener(mOnLibraryDownloadListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mClementineLibraryDownloader != null) {
            mClementineLibraryDownloader
                    .removeOnLibraryDownloadListener(mOnLibraryDownloadListener);
            mProgressDialog.dismiss();
        }
    }

    @SuppressLint({"InlinedApi", "NewApi"})
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_library, container,
                false);

        mSwipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.library_refresh_layout);
        mEmptyLibrary = (SwipeRefreshLayout) view.findViewById(R.id.library_refresh_empty_layout);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        mEmptyLibrary.setOnRefreshListener(this);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.orange);
        mEmptyLibrary.setColorSchemeResources(R.color.orange);

        mList = (ListView) view.findViewById(R.id.library);

        mLibraryEmptyText = (TextView) mEmptyLibrary.findViewById(R.id.library_empty_txt);

        mList.setOnItemClickListener(oiclLibraryClick);
        mList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mList.setMultiChoiceModeListener(new MultiChoiceModeListener() {
            @Override
            public boolean onActionItemClicked(ActionMode mode,
                    android.view.MenuItem item) {
                SparseBooleanArray checkedPositions = mList.getCheckedItemPositions();
                final LinkedList<LibrarySelectItem> selectedItems = new LinkedList<>();
                final LinkedList<String> urls = new LinkedList<>();
                mLibraryQueriesDone = 0;

                for (int i = 0; i < checkedPositions.size(); ++i) {
                    int position = checkedPositions.keyAt(i);
                    if (checkedPositions.valueAt(i)) {
                        selectedItems.add(mAdapters.getLast().getItem(position));
                    }
                }

                for (LibrarySelectItem libraryItem : selectedItems) {
                    OnLibrarySelectFinishedListener listener;

                    switch (item.getItemId()) {
                        case R.id.library_context_add:

                            listener = new OnLibrarySelectFinishedListener() {
                                @Override
                                public void OnLibrarySelectFinished(LinkedList<LibrarySelectItem> l) {
                                    addSongsToPlaylist(l);
                                }
                            };

                            break;
                        case R.id.library_context_download:
                            listener = new OnLibrarySelectFinishedListener() {
                                @Override
                                public void OnLibrarySelectFinished(LinkedList<LibrarySelectItem> l) {
                                    for (LibrarySelectItem libItem : l) {
                                        urls.add(libItem.getUrl());
                                    }
                                    mLibraryQueriesDone++;

                                    // Have we got all queries?
                                    if (mLibraryQueriesDone == selectedItems.size() && !urls
                                            .isEmpty()) {
                                        DownloadManager.getInstance()
                                                .addJob(ClementineMessageFactory
                                                        .buildDownloadSongsMessage(
                                                                ClementineRemoteProtocolBuffer.DownloadItem.Urls,
                                                                urls));
                                    }

                                }
                            };

                            break;
                        default:
                            return false;
                    }
                    queryLibraryItems(libraryItem, listener);
                }
                mode.finish();
                return true;
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode,
                    android.view.Menu menu) {
                android.view.MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.library_context_menu, menu);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    getActivity().getWindow()
                            .setStatusBarColor(getResources().getColor(R.color.grey_cab_status));
                }

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode,
                    android.view.Menu menu) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    getActivity().getWindow()
                            .setStatusBarColor(getResources().getColor(R.color.actionbar_dark));
                }
            }

            @Override
            public void onItemCheckedStateChanged(ActionMode mode,
                    int position, long id, boolean checked) {
            }
        });

        // Check if we have the correct Clementine library. Otherwise delete it
        LibraryDatabaseHelper libraryDatabaseHelper = new LibraryDatabaseHelper();
        libraryDatabaseHelper.removeDatabaseIfFromOtherClementine();

        LibraryGroup libraryGroup = LibraryGroup.getSelectedLibraryGroup(getActivity());
        mLibraryLevels = libraryGroup.getMaxLevels();

        // Create the adapter
        if (mClementineLibraryDownloader == null && libraryDatabaseHelper.databaseExists()) {
            libraryGroup.setLevel(0);
            LibraryAdapter a = new LibraryAdapter(getActivity(), libraryGroup);
            mAdapters.add(a);
        }

        showList();

        mActionBar.setTitle("");
        mActionBar.setSubtitle(" / ");

        setHasOptionsMenu(true);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mAdapters.clear();
    }

    private OnLibraryDownloadListener mOnLibraryDownloadListener = new OnLibraryDownloadListener() {

        @Override
        public void OnLibraryDownloadFinished(DownloaderResult result) {
            mProgressDialog.dismiss();
            mClementineLibraryDownloader = null;
            mSwipeRefreshLayout.setRefreshing(false);
            mEmptyLibrary.setRefreshing(false);

            if (result.getResult() == DownloadResult.SUCCESSFUL) {
                LibraryGroup libraryGroup = LibraryGroup.getSelectedLibraryGroup(getActivity());
                libraryGroup.setLevel(0);
                LibraryAdapter a = new LibraryAdapter(getActivity(), libraryGroup);
                mAdapters.add(a);
                showList();
            } else {
                Utilities.ShowMessageDialog(getActivity(), R.string.library_download_error,
                        result.getMessageStringId());
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

    private void queryLibraryItems(LibrarySelectItem libraryItem,
            OnLibrarySelectFinishedListener onLibrarySelectFinishedListener) {

        if (libraryItem.getLevel() == mLibraryLevels-1) {
            LinkedList<LibrarySelectItem> result = new LinkedList<>();
            result.add(libraryItem);
            onLibrarySelectFinishedListener.OnLibrarySelectFinished(result);
        } else {
            LibraryGroup libraryGroup = LibraryGroup.getSelectedLibraryGroup(getActivity());
            libraryGroup.setLevel(mLibraryLevels-1);
            libraryGroup.setSelection(libraryItem.getSelection());
            libraryGroup.addOnLibrarySelectFinishedListener(onLibrarySelectFinishedListener);
            libraryGroup.selectDataAsync();
        }
    }

    private void setActionBarTitle() {
        MySong currentSong = App.Clementine.getCurrentSong();
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
        mList.setDivider(null);
        mList.setDividerHeight(0);

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
                mActionBar.setSubtitle("/");
            } else {
                LibrarySelectItem item = adapter.getItem(0);

                StringBuilder sb = new StringBuilder();
                for (int i=0;i<mAdapters.size()-1;i++) {
                    sb.append(" / ");
                    sb.append(item.getSelection()[i]);
                }
                mActionBar.setSubtitle(sb.toString());
            }
        }
    }

    private void addSongsToPlaylist(LinkedList<LibrarySelectItem> l) {
        Message msg = Message.obtain();
        LinkedList<String> urls = new LinkedList<>();
        for (LibrarySelectItem item : l) {
            urls.add(item.getUrl());
        }

        msg.obj = ClementineMessageFactory.buildInsertUrl(
                App.Clementine.getPlaylistManager().getActivePlaylistId(), urls);

        App.ClementineConnection.mHandler.sendMessage(msg);

        Toast.makeText(getActivity(),
                String.format(getString(R.string.library_songs_added), urls.size()),
                Toast.LENGTH_SHORT).show();
    }

    private OnItemClickListener oiclLibraryClick = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {
            LibrarySelectItem item = mAdapters.getLast().getItem(position);

            if (item.getLevel() == mLibraryLevels-1) {
                Message msg = Message.obtain();
                LinkedList<String> urls = new LinkedList<>();
                urls.add(item.getUrl());
                msg.obj = ClementineMessageFactory.buildInsertUrl(
                        App.Clementine.getPlaylistManager().getActivePlaylistId(), urls);
                App.ClementineConnection.mHandler.sendMessage(msg);

                Toast.makeText(getActivity(),
                        String.format(getString(R.string.library_songs_added), 1),
                        Toast.LENGTH_SHORT).show();
            } else {
                LibraryGroup libraryGroup = LibraryGroup.getSelectedLibraryGroup(getActivity());
                libraryGroup.setLevel(mAdapters.size());
                libraryGroup.setSelection(item.getSelection());
                mAdapters.add(new LibraryAdapter(getActivity(), libraryGroup));
                showList();
            }
        }
    };

    @Override
    public void onRefresh() {
        mAdapters.clear();
        showList();

        mClementineLibraryDownloader = new ClementineLibraryDownloader(getActivity());
        mClementineLibraryDownloader.addOnLibraryDownloadListener(
                mOnLibraryDownloadListener);
        mClementineLibraryDownloader.startDownload(ClementineMessage
                .getMessage(MsgType.GET_LIBRARY));

        createDownloadProgressDialog();
    }
}
