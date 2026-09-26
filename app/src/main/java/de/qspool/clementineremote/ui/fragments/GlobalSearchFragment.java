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
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.ui.interfaces.BackPressHandleable;
import de.qspool.clementineremote.ui.interfaces.RemoteDataReceiver;
import de.qspool.clementineremote.ui.search.SearchViewModel;
import de.qspool.clementineremote.ui.search.SearchViews;

/**
 * Search, drawn in Compose ({@code SearchScreen}), with its own search bar. This fragment hands
 * back presses to it, which goes up a level of the results.
 */
public class GlobalSearchFragment extends Fragment implements BackPressHandleable, RemoteDataReceiver {

    private SearchViewModel mViewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mViewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        actionBar.setTitle("");
        actionBar.setSubtitle("");

        ComposeView view = new ComposeView(requireContext());
        SearchViews.showSearch(view, mViewModel);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // The search bar is on the screen; nothing for the app bar.
        menu.clear();
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void MessageFromClementine(ClementineMessage clementineMessage) {
        // Search results arrive through GlobalSearchManager, which the view model follows.
    }

    @Override
    public boolean onBackPressed() {
        return mViewModel.back();
    }
}
