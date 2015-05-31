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
import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedList;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.database.SongSelectItem;
import de.qspool.clementineremote.backend.globalsearch.GlobalSearchManager;
import de.qspool.clementineremote.backend.globalsearch.GlobalSearchQuery;
import de.qspool.clementineremote.backend.library.LibraryQuery;
import de.qspool.clementineremote.backend.listener.OnGlobalSearchResponseListener;
import de.qspool.clementineremote.backend.listener.OnSongSelectFinishedListener;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer;
import de.qspool.clementineremote.ui.adapter.DynamicSongQueryAdapter;
import de.qspool.clementineremote.ui.interfaces.BackPressHandleable;
import de.qspool.clementineremote.ui.interfaces.RemoteDataReceiver;

public class GlobalSearchFragment extends Fragment implements BackPressHandleable, RemoteDataReceiver, SharedPreferences.OnSharedPreferenceChangeListener {

    private ActionBar mActionBar;

    private SwipeRefreshLayout mSwipeRefreshLayout;

    private SwipeRefreshLayout mEmptyView;

    private TextView mEmptyText;

    private ListView mList;

    private LinkedList<DynamicSongQueryAdapter> mAdapters = new LinkedList<>();

    private int mMaxLevels;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the actionbar
        mActionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        setHasOptionsMenu(true);
    }

    @SuppressLint({"InlinedApi", "NewApi"})
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_global_search, container,
                false);

        mSwipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.global_search_refresh_layout);
        mEmptyView = (SwipeRefreshLayout) view.findViewById(R.id.global_search_empty);

        mSwipeRefreshLayout.setColorSchemeResources(R.color.orange);
        mEmptyView.setColorSchemeResources(R.color.orange);
        mSwipeRefreshLayout.setEnabled(true);
        mEmptyView.setEnabled(true);

        mEmptyText = (TextView) view.findViewById(R.id.global_search_empty_txt);
        mList = (ListView) view.findViewById(R.id.global_search);

        mList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                SongSelectItem item = mAdapters.getLast().getItem(position);

                if (item.getLevel() == mMaxLevels-1) {
                    Message msg = Message.obtain();
                    LinkedList<String> urls = new LinkedList<>();
                    urls.add(item.getUrl());
                    msg.obj = ClementineMessageFactory.buildInsertUrl(
                            App.Clementine.getPlaylistManager().getActivePlaylistId(), urls);
                    App.ClementineConnection.mHandler.sendMessage(msg);

                    Toast.makeText(getActivity(),
                            String.format(getString(R.string.songs_added), 1),
                            Toast.LENGTH_SHORT).show();
                } else {
                    GlobalSearchQuery globalSearchQuery = new GlobalSearchQuery(getActivity());
                    globalSearchQuery.openDatabase();
                    globalSearchQuery.setLevel(mAdapters.size());
                    globalSearchQuery.setSelection(item.getSelection());
                    mAdapters.add(new DynamicSongQueryAdapter(getActivity(), globalSearchQuery));
                    showList();
                }
            }
        });
        mList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mList.setMultiChoiceModeListener(new MultiChoiceModeListener() {
            @Override
            public boolean onActionItemClicked(ActionMode mode,
                    android.view.MenuItem item) {
                SparseBooleanArray checkedPositions = mList.getCheckedItemPositions();
                final LinkedList<SongSelectItem> selectedItems = new LinkedList<>();

                for (int i = 0; i < checkedPositions.size(); ++i) {
                    int position = checkedPositions.keyAt(i);
                    if (checkedPositions.valueAt(i)) {
                        selectedItems.add(mAdapters.getLast().getItem(position));
                    }
                }

                for (SongSelectItem songItem : selectedItems) {
                    OnSongSelectFinishedListener listener;

                    switch (item.getItemId()) {
                        case R.id.global_search_context_add:

                            listener = new OnSongSelectFinishedListener() {
                                @Override
                                public void OnSongSelectFinished(
                                        LinkedList<SongSelectItem> l) {
                                    addSongsToPlaylist(l);
                                }
                            };

                            break;
                        default:
                            return false;
                    }
                    queryItems(songItem, listener);
                }
                mode.finish();
                return true;
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode,
                    Menu menu) {
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.global_search_context_menu, menu);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    getActivity().getWindow()
                            .setStatusBarColor(getResources().getColor(R.color.grey_cab_status));
                }

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode,
                    Menu menu) {
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

        GlobalSearchManager.getInstance().addOnGlobalSearchResponseListerner(
                new OnGlobalSearchResponseListener() {
                    @Override
                    public void onStatusChanged(int id,
                            final ClementineRemoteProtocolBuffer.GlobalSearchStatus status) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (status
                                        == ClementineRemoteProtocolBuffer.GlobalSearchStatus.GlobalSearchFinished) {
                                    GlobalSearchQuery globalSearchQuery = new GlobalSearchQuery(
                                            getActivity());
                                    mMaxLevels = globalSearchQuery.getMaxLevels();

                                    // Create the adapter
                                    globalSearchQuery.openDatabase();
                                    globalSearchQuery.setLevel(0);
                                    DynamicSongQueryAdapter a = new DynamicSongQueryAdapter(
                                            getActivity(), globalSearchQuery);
                                    mAdapters.add(a);
                                    showList();

                                    mEmptyView.setRefreshing(false);
                                    mSwipeRefreshLayout.setRefreshing(false);
                                }
                            }
                        });
                    }

                    @Override
                    public void onResultsReceived(int id) {
                    }
                });

        setHasOptionsMenu(true);

        mActionBar.setTitle("");
        mActionBar.setSubtitle("/");

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.global_search_menu, menu);

        // Create a listener for search change
        final MenuItem search = menu.findItem(R.id.global_search_menu_search);
        final SearchView searchView = (SearchView) search.getActionView();
        searchView.setIconifiedByDefault(true);
        searchView.setIconified(false);

        final SearchView.OnQueryTextListener queryTextListener
                = new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }

            @Override
            public boolean onQueryTextSubmit(String query) {
                mSwipeRefreshLayout.setRefreshing(true);
                mEmptyView.setRefreshing(true);

                Message msg = Message.obtain();

                msg.obj = ClementineMessageFactory.buildGlobalSearch(query);
                App.ClementineConnection.mHandler.sendMessage(msg);

                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
                        Activity.INPUT_METHOD_SERVICE);
                imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);

                // Set the actionbar title
                mActionBar.setTitle(getResources().getString(R.string.global_search_query, query));
                mActionBar.setSubtitle("/");

                // Query must be empty in order to collapse the search view.
                searchView.setQuery("", false);
                searchView.setIconified(true);

                return true;
            }
        };
        searchView.setOnQueryTextListener(queryTextListener);
        searchView.setQueryHint(getString(R.string.global_search_search));

        EditText searchText = (EditText) searchView.findViewById(
                android.support.v7.appcompat.R.id.search_src_text);
        searchText.setHintTextColor(getResources().getColor(R.color.searchview_edittext_hint));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mList.setFastScrollEnabled(true);
        mList.setTextFilterEnabled(true);
        mList.setSelector(new ColorDrawable(android.R.color.transparent));
        mList.setDivider(null);
        mList.setDividerHeight(0);

        mList.setEmptyView(mEmptyView);
    }

    @Override
    public void MessageFromClementine(ClementineMessage clementineMessage) {
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
            mList.setEmptyView(mEmptyView);
        } else {
            DynamicSongQueryAdapter adapter = mAdapters.getLast();
            mList.setAdapter(adapter);
            if (adapter.isEmpty()) {
                mActionBar.setSubtitle("/ ");
            } else {
                SongSelectItem item = adapter.getItem(0);

                StringBuilder sb = new StringBuilder();
                sb.append("/ ");
                for (int i=0;i<mAdapters.size()-1;i++) {
                    sb.append(item.getSelection()[i]);
                    if (i<mAdapters.size()-2)
                        sb.append(" / ");
                }
                mActionBar.setSubtitle(sb.toString());
            }
        }
    }

    private void queryItems(SongSelectItem item,
            OnSongSelectFinishedListener onSongSelectFinishedListener) {

        if (item.getLevel() == mMaxLevels-1) {
            LinkedList<SongSelectItem> result = new LinkedList<>();
            result.add(item);
            onSongSelectFinishedListener.OnSongSelectFinished(result);
        } else {
            LibraryQuery libraryQuery = new LibraryQuery(getActivity());
            libraryQuery.openDatabase();
            libraryQuery.setLevel(mMaxLevels-1);
            libraryQuery.setSelection(item.getSelection());
            libraryQuery.addOnLibrarySelectFinishedListener(onSongSelectFinishedListener);
            libraryQuery.selectDataAsync();
        }
    }

    private void addSongsToPlaylist(LinkedList<SongSelectItem> l) {
        Message msg = Message.obtain();
        LinkedList<String> urls = new LinkedList<>();
        for (SongSelectItem item : l) {
            Log.d("GS", "Insert url " + item.getUrl());
            urls.add(item.getUrl());
        }

        msg.obj = ClementineMessageFactory.buildInsertUrl(
                App.Clementine.getPlaylistManager().getActivePlaylistId(), urls);

        App.ClementineConnection.mHandler.sendMessage(msg);

        Toast.makeText(getActivity(),
                String.format(getString(R.string.songs_added), urls.size()),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(SharedPreferencesKeys.SP_LIBRARY_GROUPING)
                || key.equals(SharedPreferencesKeys.SP_LIBRARY_SORTING)) {
            mAdapters.clear();

            showList();
        }
    }
}
