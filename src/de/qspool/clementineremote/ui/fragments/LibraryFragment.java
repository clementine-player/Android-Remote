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

import java.util.LinkedList;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.SearchView;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.ClementineLibraryDownloader;
import de.qspool.clementineremote.backend.event.OnLibraryDownloadListener;
import de.qspool.clementineremote.backend.event.OnLibrarySelectFinishedListener;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.player.MyLibrary;
import de.qspool.clementineremote.backend.player.MyLibraryItem;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.ui.adapter.LibraryAdapter;

public class LibraryFragment extends AbstractDrawerFragment implements
		OnLibrarySelectFinishedListener {
	private final String TAG = "LibraryFragment";
	
	private ActionBar mActionBar;
	private ListView mList;
	private LinkedList<LibraryAdapter> mAdapters = new LinkedList<LibraryAdapter>();

	private MyLibrary mLibrary;

	private View mEmptyLibrary;
	private TextView mLibraryPath;
	
	private ProgressDialog mProgressDialog;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");

		// Get the actionbar
		mActionBar = getSherlockActivity().getSupportActionBar();
		setHasOptionsMenu(true);
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG, "onResume");
		// Check if we are still connected
		if (App.mClementineConnection == null || App.mClementine == null
				|| !App.mClementineConnection.isConnected()) {
		} else {
			// RequestPlaylistSongs();
			setActionBarTitle();
			if (App.libraryDownloader != null) {
				createDownloadProgressDialog();
				App.libraryDownloader.addOnLibraryDownloadListener(mOnLibraryDownloadListener);
			}
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		Log.d(TAG, "onPause");
		
		if (App.libraryDownloader != null) {
			App.libraryDownloader.removeOnLibraryDownloadListener(mOnLibraryDownloadListener);
			mProgressDialog.dismiss();
		}
		
		if (mLibrary != null) {
			mLibrary.closeDatabase();
			mAdapters.clear();
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.library_fragment, container,
				false);
		
		Log.d(TAG, "onCreateView");

		mLibrary = new MyLibrary(getActivity());
		mLibrary.removeDatabaseIfFromOtherClementine();

		mLibraryPath = (TextView) view.findViewById(R.id.library_path);
		mList = (ListView) view.findViewById(R.id.library);
		mEmptyLibrary = view.findViewById(R.id.library_empty);

		// Create the adapter
		if (App.libraryDownloader == null && mLibrary.databaseExists()) {
			mLibrary = new MyLibrary(getActivity());
			mLibrary.openDatabase();
			LibraryAdapter a = new LibraryAdapter(getActivity(), mLibrary.getArtists(), mLibrary, MyLibrary.LVL_ARTIST);
			mAdapters.add(a);
		}

		mList.setOnItemClickListener(oiclLibraryClick);
		registerForContextMenu(mList);

		showList();

		mActionBar.setTitle("");
		mActionBar.setSubtitle("");

		setHasOptionsMenu(true);

		return view;
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
		public void OnLibraryDownloadFinished(boolean successful) {
			mProgressDialog.dismiss();
			App.libraryDownloader = null;
			
			mLibrary = new MyLibrary(getActivity());
			mLibrary.openDatabase();
			LibraryAdapter a = new LibraryAdapter(getActivity(), mLibrary.getArtists(), mLibrary, MyLibrary.LVL_ARTIST);
			mAdapters.add(a);
			showList();
		}

		@Override
		public void OnProgressUpdate(int progress) {
			mProgressDialog.setProgress(progress);
		}

		@Override
		public void OnOptimizeLibrary() {
			Log.d(TAG, "OnOptimizeLibrary");
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
		mProgressDialog.setMax(100);
		mProgressDialog.setProgress(0);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mProgressDialog.setCancelable(false);
		
		mProgressDialog.show();
	}
	
	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
	    AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
	    switch (item.getItemId()) {
	        case R.id.library_context_add:
	        	MyLibraryItem libraryItem = mAdapters.getLast().getItem(info.position);

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
					return false;
				default:
					return false;
				}
	        default:
	            return super.onContextItemSelected(item);
	    }
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.library_menu, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	                                ContextMenuInfo menuInfo) {
	    super.onCreateContextMenu(menu, v, menuInfo);
	    android.view.MenuInflater inflater = getActivity().getMenuInflater();
	    inflater.inflate(R.menu.library_context_menu, menu);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		// Create a listener for search change
		SearchView searchView = (SearchView) menu.findItem(
				R.id.library_menu_search).getActionView();

		final SearchView.OnQueryTextListener queryTextListener = new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextChange(String newText) {
				// Set the filter text as the fragments might not yet
				// created. Only the left and right fragment from the
				// currently active is created (onCreate() called).
				// Therefore the other adapters are not yet created,
				// onCreate filters for this string given in setFilterText()
				if (mAdapters.getLast() != null) {
					mAdapters.getLast().getFilter().filter(newText);
				}
				return true;
			}

			@Override
			public boolean onQueryTextSubmit(String query) {
				// Do something
				if (mAdapters.getLast() != null) {
					mAdapters.getLast().getFilter().filter(query);
				}

				return true;
			}
		};
		searchView.setOnQueryTextListener(queryTextListener);
		searchView.setQueryHint(getString(R.string.playlist_search_hint));

		super.onPrepareOptionsMenu(menu);
	}

	private void setActionBarTitle() {
		MySong currentSong = App.mClementine.getCurrentSong();
		if (currentSong == null) {
			mActionBar.setTitle(getString(R.string.player_nosong));
			mActionBar.setSubtitle("");
		} else {
			mActionBar.setTitle(currentSong.getArtist());
			mActionBar.setSubtitle(currentSong.getTitle());
		}
	}

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mList.setFastScrollEnabled(true);
		mList.setTextFilterEnabled(true);
		mList.setSelector(android.R.color.transparent);
		mList.setOnItemClickListener(oiclLibraryClick);
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
		if (mAdapters.size() <= 1)
			return false;

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
			adapter.getFilter().filter("");
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

		mLibraryPath.setText(sb.toString());
	}
	
	private void addSongsToPlaylist(LinkedList<MyLibraryItem> l) {
		Message msg = Message.obtain();
		LinkedList<String> urls = new LinkedList<String>();
		for (MyLibraryItem item : l) {
			urls.add(item.getUrl());
		}
		
		msg.obj = ClementineMessageFactory.buildInsertUrl(
				App.mClementine.getActivePlaylist().getId(), urls);
		
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
				LibraryAdapter album = new LibraryAdapter(getActivity(), mLibrary.getAlbums(item.getArtist()), mLibrary, MyLibrary.LVL_ALBUM);
				mAdapters.add(album);
				showList();
				break;
			case MyLibrary.LVL_ALBUM:
				LibraryAdapter title = new LibraryAdapter(getActivity(), mLibrary.getTitles(item.getArtist(), item.getAlbum()), mLibrary, MyLibrary.LVL_TITLE);
				mAdapters.add(title);
				showList();
				break;
			case MyLibrary.LVL_TITLE:
				Message msg = Message.obtain();
				LinkedList<String> urls = new LinkedList<String>();
				urls.add(item.getUrl());
				msg.obj = ClementineMessageFactory.buildInsertUrl(
						App.mClementine.getActivePlaylist().getId(), urls);
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
