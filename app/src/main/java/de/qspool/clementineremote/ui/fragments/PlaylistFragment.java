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

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.compose.ui.platform.ComposeView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.ui.interfaces.BackPressHandleable;
import de.qspool.clementineremote.ui.interfaces.RemoteDataReceiver;
import de.qspool.clementineremote.ui.queue.QueueViewModel;
import de.qspool.clementineremote.ui.queue.QueueViews;

/**
 * The queue, drawn in Compose ({@code QueueScreen}). This fragment keeps the app bar's menu:
 * searching the playlist, and downloading, clearing or closing it.
 */
public class PlaylistFragment extends Fragment implements BackPressHandleable, RemoteDataReceiver {

    private QueueViewModel mViewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mViewModel = new ViewModelProvider(this).get(QueueViewModel.class);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        actionBar.setTitle("");
        actionBar.setSubtitle("");

        ComposeView view = new ComposeView(requireContext());
        QueueViews.showQueue(view, mViewModel);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Fetch the songs of playlists not downloaded yet.
        mViewModel.load();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.playlist_menu, menu);

        SearchView searchView = (SearchView) menu.findItem(R.id.playlist_menu_search)
                .getActionView();
        searchView.setQueryHint(getString(R.string.playlist_search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String newText) {
                mViewModel.setFilter(newText);
                return true;
            }

            @Override
            public boolean onQueryTextSubmit(String query) {
                mViewModel.setFilter(query);
                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.download_playlist) {
            mViewModel.downloadPlaylist();
            return true;
        } else if (id == R.id.clear_playlist) {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.playlist_clear)
                    .setMessage(R.string.playlist_clear_content)
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .setPositiveButton(R.string.playlist_clear_confirm,
                            (dialog, which) -> mViewModel.clearPlaylist())
                    .show();
            return true;
        } else if (id == R.id.close_playlist) {
            mViewModel.closePlaylist();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void MessageFromClementine(ClementineMessage clementineMessage) {
        // The queue follows Clementine through its view model.
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }
}
