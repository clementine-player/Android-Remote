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

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.downloader.ClementineSongDownloader;
import de.qspool.clementineremote.backend.downloader.DownloadManager;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.ui.adapter.DownloaderAdapter;
import de.qspool.clementineremote.ui.interfaces.BackPressHandleable;
import de.qspool.clementineremote.ui.interfaces.RemoteDataReceiver;
import de.qspool.clementineremote.utils.Utilities;

public class DownloadsFragment extends Fragment implements BackPressHandleable, RemoteDataReceiver {

    private ActionBar mActionBar;

    private ListView mList;

    private DownloaderAdapter mAdapter;

    private Timer mUpdateTimer;

    private View mEmptyDownloads;

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
        if (App.ClementineConnection == null
                || App.Clementine == null
                || !App.ClementineConnection.isConnected()) {
        } else {
            //RequestPlaylistSongs();
            setActionBarTitle();
            mUpdateTimer = new Timer();
            mUpdateTimer.scheduleAtFixedRate(getTimerTask(), 250, 250);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mUpdateTimer != null) {
            mUpdateTimer.cancel();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_downloads,
                container, false);

        mList = (ListView) view.findViewById(R.id.downloads);
        mEmptyDownloads = view.findViewById(R.id.downloads_empty);

        // Create the adapter
        mAdapter = new DownloaderAdapter(getActivity(), R.layout.item_download,
                DownloadManager.getInstance().getAllDownloaders());

        mList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final ClementineSongDownloader downloader = (ClementineSongDownloader) mList.getAdapter()
                        .getItem(position);
                if (downloader.getStatus() == AsyncTask.Status.FINISHED) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

                    builder.setTitle(R.string.downloaded_songs);
                    String[] songs = new String[downloader.getDownloadedSongs().size()];
                    for (int i=0;i<songs.length;i++) {
                        ClementineSongDownloader.DownloadedSong ds = downloader.getDownloadedSongs().get(i);
                        songs[i] = ds.song.getArtist() + " - " + ds.song.getTitle();
                    }
                    builder.setItems(songs, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            playFile(downloader.getDownloadedSongs().get(which).uri);
                        }
                    });

                    builder.setNegativeButton(R.string.dialog_close, null);
                    builder.show();
                }
            }
        });
        mList.setAdapter(mAdapter);

        mActionBar.setTitle("");
        mActionBar.setSubtitle("");

        setHasOptionsMenu(true);

        return view;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
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
        mList.setDivider(null);
        mList.setDividerHeight(0);
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

    private void playFile(Uri file) {
        Intent mediaIntent = new Intent();
        mediaIntent.setAction(Intent.ACTION_VIEW);
        mediaIntent.setDataAndType(file, "audio/*");
        mediaIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (mediaIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivity(mediaIntent);
        } else {
            Toast.makeText(getActivity(), R.string.app_not_available, Toast.LENGTH_LONG)
                    .show();
        }
    }

    /**
     * Creates a timer task for refeshing the download list
     *
     * @return Task to update download list
     */
    private TimerTask getTimerTask() {
        return new TimerTask() {

            @Override
            public void run() {
                if (mAdapter != null && getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            if (getActivity() == null) {
                                return;
                            }

                            mAdapter.notifyDataSetChanged();
                            if (DownloadManager.getInstance().getAllDownloaders().isEmpty()) {
                                mList.setEmptyView(mEmptyDownloads);
                            }

                            StringBuilder sb = new StringBuilder();
                            sb.append(getActivity().getString(R.string.download_freespace));
                            sb.append(": ");
                            sb.append(Utilities
                                    .humanReadableBytes((long) Utilities.getFreeSpaceExternal(),
                                            true));
                            mActionBar.setSubtitle(sb.toString());
                        }

                    });
                }
            }

        };
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }
}
