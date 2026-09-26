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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.RemoteRepository;
import de.qspool.clementineremote.backend.downloader.DownloadManager;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.player.LyricsProvider;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.ui.dialogs.DownloadChooserDialog;
import de.qspool.clementineremote.ui.dialogs.ProgressDialog;
import de.qspool.clementineremote.ui.interfaces.BackPressHandleable;
import de.qspool.clementineremote.ui.interfaces.RemoteDataReceiver;
import de.qspool.clementineremote.ui.player.PlayerViewModel;
import de.qspool.clementineremote.ui.player.PlayerViews;
import de.qspool.clementineremote.utils.Utilities;

/**
 * The player, drawn in Compose ({@code PlayerScreen}): the player, song details and connection
 * pages, and the controls. This fragment keeps the app bar's menu for the page shown, and the
 * lyrics, which the artwork asks for.
 */
public class PlayerFragment extends Fragment implements BackPressHandleable, RemoteDataReceiver {

    private static final int PAGE_PLAYER = 0;

    private static final int PAGE_DETAILS = 1;

    private ActionBar mActionBar;

    private PlayerViewModel mViewModel;

    private int mPage = PAGE_PLAYER;

    private ProgressDialog mPdDownloadLyrics;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the actionbar
        mActionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        mActionBar.setTitle(R.string.player_playlist);

        mViewModel = new ViewModelProvider(this).get(PlayerViewModel.class);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ComposeView view = new ComposeView(requireContext());
        PlayerViews.showPlayer(view, this::requestLyrics, page -> {
            mPage = page;
            requireActivity().invalidateOptionsMenu();
        });

        metadataChanged();

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();

        if (mPage == PAGE_PLAYER) {
            inflater.inflate(R.menu.player_menu, menu);
        } else if (mPage == PAGE_DETAILS) {
            inflater.inflate(R.menu.song_info_menu, menu);

            // Shall we show the lastfm buttons?
            boolean showLastFm = App.getPreferences()
                    .getBoolean(SharedPreferencesKeys.SP_LASTFM, true);
            menu.findItem(R.id.love).setVisible(showLastFm);
            menu.findItem(R.id.ban).setVisible(showLastFm);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.download) {
            download();
        } else if (id == R.id.stop) {
            RemoteRepository.send(ClementineMessage.getMessage(MsgType.STOP));
        } else if (id == R.id.love) {
            mViewModel.love();
            Toast.makeText(getActivity(), R.string.track_loved, Toast.LENGTH_SHORT).show();
        } else if (id == R.id.ban) {
            mViewModel.ban();
            Toast.makeText(getActivity(), R.string.track_banned, Toast.LENGTH_SHORT).show();
        } else {
            return false;
        }
        return true;
    }

    private void download() {
        if (App.Clementine.getCurrentSong() == null) {
            Toast.makeText(getActivity(), R.string.player_nosong, Toast.LENGTH_LONG).show();
            return;
        }
        if (!App.Clementine.getCurrentSong().isLocal()) {
            Toast.makeText(getActivity(), R.string.player_song_is_stream, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        DownloadChooserDialog downloadChooserDialog = new DownloadChooserDialog(getActivity());
        downloadChooserDialog.setCallback(new DownloadChooserDialog.Callback() {
            @Override
            public void onItemClick(DownloadChooserDialog.Type type) {
                switch (type) {
                    case SONG:
                        DownloadManager.getInstance().addJob(ClementineMessageFactory
                                .buildDownloadSongsMessage(DownloadItem.CurrentItem));
                        break;
                    case ALBUM:
                        DownloadManager.getInstance().addJob(ClementineMessageFactory
                                .buildDownloadSongsMessage(DownloadItem.ItemAlbum));
                        break;
                    case PLAYLIST:
                        DownloadManager.getInstance().addJob(ClementineMessageFactory
                                .buildDownloadSongsMessage(DownloadItem.APlaylist,
                                        App.Clementine.getPlaylistManager().getActivePlaylistId()));
                        break;
                }
            }
        });
        downloadChooserDialog.showDialog();
    }

    @Override
    public void MessageFromClementine(ClementineMessage clementineMessage) {
        if (clementineMessage.getMessageType() == MsgType.CURRENT_METAINFO) {
            metadataChanged();
        } else if (clementineMessage.getMessageType() == MsgType.LYRICS) {
            showLyricsDialog();
        }
    }

    private void metadataChanged() {
        // ActionBar shows the current playlist
        if (App.Clementine.getPlaylistManager().getActivePlaylist() != null) {
            mActionBar.setSubtitle(
                    App.Clementine.getPlaylistManager().getActivePlaylist().getName());
        }
    }

    /** Shows the current song's lyrics, asking Clementine for them first if need be. */
    private void requestLyrics() {
        MySong song = App.Clementine.getCurrentSong();
        if (song == null) {
            return;
        }
        mPdDownloadLyrics = ProgressDialog.showIndeterminate(getActivity(), 0,
                R.string.player_download_lyrics, true, null);
        if (song.getLyricsProvider().isEmpty()) {
            RemoteRepository.send(ClementineMessage.getMessage(MsgType.GET_LYRICS));
        } else {
            showLyricsDialog();
        }
    }

    /**
     * Opens a dialog to show the lyrics
     */
    private void showLyricsDialog() {
        // Only show lyrics dialog, if the user is still waiting for it
        if (mPdDownloadLyrics == null || !mPdDownloadLyrics.isShowing()) {
            return;
        }

        // Dismiss the dialog
        mPdDownloadLyrics.dismiss();

        // Check for a valid lyric
        MySong song = App.Clementine.getCurrentSong();
        if (song == null || song.getLyricsProvider().isEmpty()) {
            Toast.makeText(getActivity(), R.string.player_no_lyrics, Toast.LENGTH_SHORT).show();
            return;
        }

        // Receive the provider and show the dialog
        LyricsProvider provider = getBestLyricsProvider(song.getLyricsProvider());

        // Show the dialog
        Utilities.ShowMessageDialog(getActivity(), provider.getTitle(), provider.getContent(),
                false);
    }

    /**
     * Get the best lyrics provider for this song (currently the one with the most characters
     *
     * @param providers A list of lyrics providers
     * @return The best possible provider
     */
    private LyricsProvider getBestLyricsProvider(List<LyricsProvider> providers) {
        LyricsProvider bestProvider = providers.get(0);
        for (LyricsProvider lyric : providers) {
            // For now the provider with the longest lyrics wins
            if (lyric.getContent().length() > bestProvider.getContent().length()) {
                bestProvider = lyric;
            }
        }
        return bestProvider;
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }
}
