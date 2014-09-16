/* This file is part of the Android Clementine Remote.
 * Copyright (C) 2014, Andreas Muttscheller <asfa194@gmail.com>
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

package de.qspool.clementineremote.backend.player;

import android.os.Message;
import android.util.SparseArray;

import java.util.Collection;
import java.util.LinkedList;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.event.OnPlaylistReceivedListener;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;

public class PlaylistManager {

    private LinkedList<OnPlaylistReceivedListener> listeners
            = new LinkedList<OnPlaylistReceivedListener>();

    private SparseArray<MyPlaylist> mPlaylists = new SparseArray<MyPlaylist>();

    private int mActivePlaylist;

    private LinkedList<Integer> mPlaylistsToDownload = new LinkedList<Integer>();

    public boolean hasPlaylist(int id) {
        return mPlaylists.get(id) != null;
    }

    public void addPlaylist(MyPlaylist p) {
        mPlaylists.append(p.getId(), p);
        if (p.isActive()) {
            setActivePlaylist(p.getId());
        }

        fireOnPlaylistReceived(p);
    }

    public void removePlaylist(int id) {
        mPlaylists.remove(id);
    }

    public void removeAll() {
        mPlaylists.clear();
    }

    public void setActivePlaylist(int id) {
        mActivePlaylist = id;

        for (int i = 0; i < mPlaylists.size(); ++i) {
            mPlaylists.valueAt(i).setActive(false);
        }

        mPlaylists.get(id).setActive(true);
    }

    public boolean playlistSongsDownloaded(int id, Collection<MySong> songs) {
        MyPlaylist p = mPlaylists.get(id);
        if (p != null) {
            p.setSongs(songs);
        }

        if (mPlaylistsToDownload.contains(Integer.valueOf(id))) {
            mPlaylistsToDownload.remove(Integer.valueOf(id));
        }

        fireOnPlaylistSongsReceived(mPlaylists.get(id));
        if (mPlaylistsToDownload.isEmpty()) {
            fireOnAllRequestedPlaylistsReceived();
        }

        return p != null;
    }

    public void allPlaylistsReceived() {
        fireAllPlaylistsReceived();
    }

    public MyPlaylist getPlaylist(int id) {
        return mPlaylists.get(id);
    }

    public MyPlaylist getActivePlaylist() {
        return mPlaylists.get(getActivePlaylistId());
    }

    public int getActivePlaylistId() {
        return mActivePlaylist;
    }

    public LinkedList<MyPlaylist> getAllPlaylists() {
        LinkedList<MyPlaylist> playlists = new LinkedList<MyPlaylist>();
        for (int i = 0; i < mPlaylists.size(); ++i) {
            playlists.add(mPlaylists.valueAt(i));
        }

        return playlists;
    }

    public int requestAllPlaylistSongs() {
        int count = 0;

        for (int i = 0; i < mPlaylists.size(); i++) {
            // Get the Playlsit
            MyPlaylist playlist = mPlaylists.valueAt(i);

            if (!playlist.hasSongs()) {
                requestPlaylistSongs(playlist.getId());

                count++;
            }
        }

        return count;
    }

    public void requestPlaylistSongs(int id) {
        mPlaylistsToDownload.add(id);

        Message msg = Message.obtain();
        msg.obj = ClementineMessageFactory.buildRequestPlaylistSongs(id);
        App.mClementineConnection.mHandler.sendMessage(msg);
    }

    public void addOnPlaylistReceivedListener(OnPlaylistReceivedListener l) {
        listeners.add(l);
    }

    public void removeOnPlaylistReceivedListener(OnPlaylistReceivedListener l) {
        listeners.remove(l);
    }

    private void fireOnAllRequestedPlaylistsReceived() {
        for (OnPlaylistReceivedListener l : listeners) {
            l.onAllRequestedPlaylistSongsReceived();
        }
    }

    private void fireOnPlaylistSongsReceived(MyPlaylist p) {
        for (OnPlaylistReceivedListener l : listeners) {
            l.onPlaylistSongsReceived(p);
        }
    }

    private void fireOnPlaylistReceived(MyPlaylist p) {
        for (OnPlaylistReceivedListener l : listeners) {
            l.onPlaylistReceived(p);
        }
    }

    private void fireAllPlaylistsReceived() {
        for (OnPlaylistReceivedListener l : listeners) {
            l.onAllPlaylistsReceived();
        }
    }
}
