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

import android.os.Handler;
import android.os.Message;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.ClementinePlayerConnection;
import de.qspool.clementineremote.backend.listener.OnPlaylistReceivedListener;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;

public class PlaylistManager {

    // Iterated on the parser thread while UI and downloaders register and unregister concurrently.
    private final List<OnPlaylistReceivedListener> listeners
            = new CopyOnWriteArrayList<OnPlaylistReceivedListener>();

    private final Object mLock = new Object();

    private SparseArray<MyPlaylist> mPlaylists = new SparseArray<MyPlaylist>();

    private volatile int mActivePlaylist;

    private final List<Integer> mPlaylistsToDownload = new ArrayList<Integer>();

    public boolean hasPlaylist(int id) {
        synchronized (mLock) {
            return mPlaylists.get(id) != null;
        }
    }

    public void addPlaylist(MyPlaylist p) {
        synchronized (mLock) {
            mPlaylists.append(p.getId(), p);
        }
        if (p.isActive()) {
            setActivePlaylist(p.getId());
        }

        fireOnPlaylistReceived(p);
    }

    public void replaceAll(Collection<MyPlaylist> playlists) {
        List<MyPlaylist> added = new ArrayList<MyPlaylist>(playlists.size());
        synchronized (mLock) {
            SparseArray<MyPlaylist> updated = new SparseArray<MyPlaylist>(playlists.size());
            for (MyPlaylist p : playlists) {
                MyPlaylist previous = mPlaylists.get(p.getId());
                // Desktop re-broadcasts PLAYLISTS without sending non-active
                // PLAYLIST_SONGS; keep cached songs on unchanged background tabs
                // while clearing the active tab so fresh PLAYLIST_SONGS reload.
                if (!p.isActive() && previous != null && previous.hasSongs()
                        && p.getItemCount() == previous.getItemCount()
                        && p.getItemCount() > 0) {
                    p.setSongs(previous.getPlaylistSongs());
                }
                updated.append(p.getId(), p);
                if (p.isActive()) {
                    mActivePlaylist = p.getId();
                }
                added.add(p);
            }
            mPlaylists = updated;
            // Prune closed playlists so pending requests do not block completion.
            java.util.Iterator<Integer> pendingIt = mPlaylistsToDownload.iterator();
            while (pendingIt.hasNext()) {
                if (updated.get(pendingIt.next()) == null) {
                    pendingIt.remove();
                }
            }
        }

        // Notify listeners outside mLock so callbacks reading playlists cannot deadlock.
        for (MyPlaylist p : added) {
            fireOnPlaylistReceived(p);
        }
        fireAllPlaylistsReceived();
    }

    public void removePlaylist(int id) {
        synchronized (mLock) {
            mPlaylists.remove(id);
            mPlaylistsToDownload.remove(Integer.valueOf(id));
        }
    }

    public void removeAll() {
        synchronized (mLock) {
            mPlaylists.clear();
            mPlaylistsToDownload.clear();
        }
    }

    public void setActivePlaylist(int id) {
        synchronized (mLock) {
            mActivePlaylist = id;

            for (int i = 0; i < mPlaylists.size(); ++i) {
                mPlaylists.valueAt(i).setActive(false);
            }

            MyPlaylist target = mPlaylists.get(id);
            if (target != null) {
                target.setActive(true);
            }
        }
    }

    public boolean playlistSongsDownloaded(int id, Collection<MySong> songs) {
        MyPlaylist p;
        boolean allDownloaded;
        synchronized (mLock) {
            p = mPlaylists.get(id);
            if (p != null) {
                p.setSongs(songs);
            }
            mPlaylistsToDownload.remove(Integer.valueOf(id));
            allDownloaded = mPlaylistsToDownload.isEmpty();
        }

        if (p != null) {
            fireOnPlaylistSongsReceived(p);
        }
        if (allDownloaded) {
            fireOnAllRequestedPlaylistsReceived();
        }

        return p != null;
    }

    public void allPlaylistsReceived() {
        fireAllPlaylistsReceived();
    }

    public MyPlaylist getPlaylist(int id) {
        synchronized (mLock) {
            return mPlaylists.get(id);
        }
    }

    public MyPlaylist getActivePlaylist() {
        synchronized (mLock) {
            return mPlaylists.get(mActivePlaylist);
        }
    }

    public int getActivePlaylistId() {
        return mActivePlaylist;
    }

    public List<MyPlaylist> getAllPlaylists() {
        synchronized (mLock) {
            List<MyPlaylist> playlists = new ArrayList<MyPlaylist>(mPlaylists.size());
            for (int i = 0; i < mPlaylists.size(); ++i) {
                playlists.add(mPlaylists.valueAt(i));
            }
            return playlists;
        }
    }

    public int requestAllPlaylistSongs() {
        List<Integer> toRequest = new ArrayList<Integer>();
        synchronized (mLock) {
            for (int i = 0; i < mPlaylists.size(); i++) {
                MyPlaylist playlist = mPlaylists.valueAt(i);
                if (!playlist.hasSongs()) {
                    toRequest.add(playlist.getId());
                }
            }
        }
        int count = 0;
        for (int id : toRequest) {
            if (sendPlaylistSongsRequest(id)) {
                count++;
            }
        }
        return count;
    }

    public void requestPlaylistSongs(int id) {
        sendPlaylistSongsRequest(id);
    }

    private boolean sendPlaylistSongsRequest(int id) {
        ClementinePlayerConnection connection = App.ClementineConnection;
        Handler handler = connection != null ? connection.mHandler : null;
        if (handler == null) {
            return false;
        }
        synchronized (mLock) {
            if (!mPlaylistsToDownload.contains(id)) {
                mPlaylistsToDownload.add(id);
            }
        }
        Message msg = Message.obtain();
        msg.obj = ClementineMessageFactory.buildRequestPlaylistSongs(id);
        handler.sendMessage(msg);
        return true;
    }

    public void clearPlaylist(int id) {
        MyPlaylist playlist;
        synchronized (mLock) {
            playlist = mPlaylists.get(id);
        }
        ClementineMessage message;
        if (App.Clementine.supportsClearPlaylist()) {
            // Clementine 1.4+ clears server-side without requiring cached indices.
            message = ClementineMessageFactory.buildClearPlaylist(id);
        } else {
            // Older servers need explicit song indices; skip wire request if unloaded.
            if (playlist == null || !playlist.hasSongs()) {
                if (playlist != null) {
                    playlist.setSongs(Collections.<MySong>emptyList());
                }
                return;
            }
            message = ClementineMessageFactory.buildRemoveMultipleSongsFromPlaylist(id,
                    playlist.getPlaylistSongs());
        }
        ClementinePlayerConnection connection = App.ClementineConnection;
        Handler handler = connection != null ? connection.mHandler : null;
        if (handler != null) {
            Message msg = Message.obtain();
            msg.obj = message;
            handler.sendMessage(msg);
        }
        if (playlist != null) {
            playlist.setSongs(Collections.<MySong>emptyList());
        }
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
