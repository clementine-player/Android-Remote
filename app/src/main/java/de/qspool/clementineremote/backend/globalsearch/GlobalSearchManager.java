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

package de.qspool.clementineremote.backend.globalsearch;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.listener.OnGlobalSearchResponseListener;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer;

public class GlobalSearchManager {

    private volatile int mCurrentId = -1;

    private volatile GlobalSearchRequest mRequest;

    private final GlobalSearchDatabaseHelper mGlobalSearchDatabaseHelper = new GlobalSearchDatabaseHelper(
            App.getApp());

    private final List<OnGlobalSearchResponseListener> mListeners = new CopyOnWriteArrayList<>();

    private final GlobalSearchProviderIconStore mGlobalSearchProviderIconStore
            = new GlobalSearchProviderIconStore();

    // Synchronizes (mCurrentId, mRequest) updates between @AnyThread reset() and @WorkerThread parsing.
    private final Object mStateLock = new Object();

    private static volatile GlobalSearchManager mInstance;

    public static GlobalSearchManager getInstance() {
        if (mInstance == null) {
            // Double-checked locking with volatile ensures no caller observes a partial instance.
            synchronized (GlobalSearchManager.class) {
                if (mInstance == null) {
                    mInstance = new GlobalSearchManager();
                }
            }
        }
        return mInstance;
    }

    private GlobalSearchManager() {
        reset();
    }

    /**
     * Forgets the search showing, when connecting to a Clementine. The results of the last one
     * stay in the database until the next search clears them: emptying it here would be a write
     * to disk on the thread connecting, and nothing reads them, as every query asks for the
     * results of one search by its id.
     */
    @AnyThread
    public void reset() {
        synchronized (mStateLock) {
            mRequest = null;
            mCurrentId = -1;
        }
    }

    @WorkerThread
    public void parseClementineMessage(ClementineMessage clementineMessage) {
        if (clementineMessage.isErrorMessage())
            return;


        switch (clementineMessage.getMessageType()) {
            case GLOBAL_SEARCH_RESULT:
                parseGlobalSearchResult(clementineMessage.getMessage().getResponseGlobalSearch());
                break;
            case GLOBAL_SEARCH_STATUS:
                parseGlobalSearchStatus(
                        clementineMessage.getMessage().getResponseGlobalSearchStatus());
                break;
        }

    }

    @WorkerThread
    private void parseGlobalSearchResult(
            ClementineRemoteProtocolBuffer.ResponseGlobalSearch responseGlobalSearch) {
        int id = responseGlobalSearch.getId();

        // Parse only results for the current request
        // Snapshot mRequest under mStateLock so a concurrent reset() cannot null it after the check.
        GlobalSearchRequest request;
        synchronized (mStateLock) {
            if (id != mCurrentId || mRequest == null) {
                return;
            }
            request = mRequest;
        }

        request.addSearchResults(responseGlobalSearch);

        // Add the icon to the store
        getGlobalSearchProviderIconStore().insertProvider(responseGlobalSearch.getSearchProvider(),
                responseGlobalSearch.getSearchProviderIcon());

        fireOnResultsReceived(id);
    }

    @WorkerThread
    private void parseGlobalSearchStatus(
            ClementineRemoteProtocolBuffer.ResponseGlobalSearchStatus responseGlobalSearchStatus) {
        int id = responseGlobalSearchStatus.getId();

        switch (responseGlobalSearchStatus.getStatus()) {
            case GlobalSearchStarted:
                // Clear on the connection thread before locking mStateLock so disk I/O won't stall reset().
                mGlobalSearchDatabaseHelper.deleteAll();
                synchronized (mStateLock) {
                    mRequest = new GlobalSearchRequest(id, mGlobalSearchDatabaseHelper);
                    mCurrentId = id;
                }
                break;
            case GlobalSearchFinished:
                synchronized (mStateLock) {
                    if (id == mCurrentId && mRequest != null) {
                        mRequest.setStatus(responseGlobalSearchStatus.getStatus());
                    }
                }
                break;
        }

        fireOnStatusChanged(
                id,
                responseGlobalSearchStatus.hasQuery() ? responseGlobalSearchStatus.getQuery() : "",
                responseGlobalSearchStatus.getStatus());
    }

    public void addOnGlobalSearchResponseListerner(OnGlobalSearchResponseListener l) {
        mListeners.add(l);
    }

    public void removeOnGlobalSearchResponseListerner(OnGlobalSearchResponseListener l) {
        mListeners.remove(l);
    }

    private void fireOnStatusChanged(int id, String query,
            ClementineRemoteProtocolBuffer.GlobalSearchStatus status) {
        for (OnGlobalSearchResponseListener l : mListeners) {
            l.onStatusChanged(id, query, status);
        }
    }

    private void fireOnResultsReceived(int id) {
        for (OnGlobalSearchResponseListener l : mListeners) {
            l.onResultsReceived(id);
        }
    }

    public GlobalSearchProviderIconStore getGlobalSearchProviderIconStore() {
        return mGlobalSearchProviderIconStore;
    }

    public GlobalSearchRequest getRequest() {
        return mRequest;
    }
}
