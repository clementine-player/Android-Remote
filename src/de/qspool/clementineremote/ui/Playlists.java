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

package de.qspool.clementineremote.ui;

import java.util.ArrayList;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.SearchView;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.player.MyPlaylist;
import de.qspool.clementineremote.ui.fragments.PlaylistSongs;

public class Playlists extends SherlockFragmentActivity implements ActionBar.TabListener {

	private ViewPager mViewPager;
	private PagerAdapter mPagerAdapter;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.playlist);
		
		final ActionBar actionBar = getSupportActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);
		
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mPagerAdapter = new PagerAdapter(getSupportFragmentManager());
		mViewPager.setAdapter(mPagerAdapter);
		mViewPager.setOnPageChangeListener(
				new OnPageChangeListener() {
					
					@Override
					public void onPageSelected(int position) {
						actionBar.setSelectedNavigationItem(position);
					}
					
					@Override
					public void onPageScrolled(int arg0, float arg1, int arg2) {
					}
					
					@Override
					public void onPageScrollStateChanged(int arg0) {
					}
		});
		
		// Now get all Playlists
		for (int i=0;i<App.mClementine.getPlaylists().size();i++) {
			// Get the Playlsit
			int key = App.mClementine.getPlaylists().keyAt(i);
			MyPlaylist playlist = App.mClementine.getPlaylists().get(key);
			
			// Create the fragment
			PlaylistSongs playlistSongs = new PlaylistSongs();
			playlistSongs.setId(playlist.getId());
			mPagerAdapter.addFragment(playlistSongs);
			
			// Create the tab
			ActionBar.Tab playlistTab  = actionBar.newTab();
			playlistTab.setTabListener(this);
			playlistTab.setText(playlist.getName());
			playlistTab.setTag(playlist);
			actionBar.addTab(playlistTab);
			
			if (playlist.isActive()) {
				mViewPager.setCurrentItem(playlistTab.getPosition());
			}
		}
		
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case android.R.id.home:
	            finish();
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inf = getSupportMenuInflater();
		inf.inflate(R.menu.playlist_menu, menu);
		
		// Create a listener for search change
		SearchView searchView = (SearchView) menu.findItem(R.id.playlist_menu_search).getActionView();
		final SearchView.OnQueryTextListener queryTextListener = new    SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String newText) {
            	for (int i=0;i< mPagerAdapter.getCount();i++) {
	            	PlaylistSongs ps = (PlaylistSongs) mPagerAdapter.getItem(i);
	            	if (ps.getAdapter() != null) {
	            		ps.getAdapter().getFilter().filter(newText);
	            	}
            	}
                return true;
            }
            @Override
            public boolean onQueryTextSubmit(String query) {
                // Do something
            	for (int i=0;i< mPagerAdapter.getCount();i++) {
	            	PlaylistSongs ps = (PlaylistSongs) mPagerAdapter.getItem(i);
	            	if (ps.getAdapter() != null) {
	            		ps.getAdapter().getFilter().filter(query);
	            	}
            	}
                return true;
            }
        };
        searchView.setOnQueryTextListener(queryTextListener);
        
		
		return true;
	}

	@Override
	public void onTabSelected(Tab tab, FragmentTransaction ignoreFt) {
		mViewPager.setCurrentItem(tab.getPosition());
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft) {

	}

	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft) {
		
	}
	
    public class PagerAdapter extends FragmentPagerAdapter {

        private final ArrayList<Fragment> mFragments = new ArrayList<Fragment>();

        public PagerAdapter(FragmentManager manager) {
            super(manager);
        }

        public void addFragment(Fragment fragment) {
            mFragments.add(fragment);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mFragments.size();
        }

        @Override
        public Fragment getItem(int position) {
            return mFragments.get(position);
        }
    }
}
