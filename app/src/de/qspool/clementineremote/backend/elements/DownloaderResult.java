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

package de.qspool.clementineremote.backend.elements;

import de.qspool.clementineremote.R;

public class DownloaderResult extends ClementineElement {

    public enum DownloadResult {SUCCESSFUL, INSUFFIANT_SPACE, NOT_MOUNTED, CONNECTION_ERROR, FOBIDDEN, ONLY_WIFI, CANCELLED, ERROR}

    private DownloadResult mResult;

    private int mId;

    public DownloaderResult(int id, DownloadResult result) {
        mId = id;
        mResult = result;
    }

    public DownloadResult getResult() {
        return mResult;
    }

    public int getId() {
        return mId;
    }

    /**
     * Returns the resource ID for the corresponding errormessage.
     * For example CONNECTION_ERROR = Connection error
     *
     * @return The resource ID for the specific message
     */
    public int getMessageStringId() {
        switch (mResult) {
            case CONNECTION_ERROR:
                return R.string.download_noti_canceled;
            case FOBIDDEN:
                return R.string.download_noti_forbidden;
            case INSUFFIANT_SPACE:
                return R.string.download_noti_insufficient_space;
            case NOT_MOUNTED:
                return R.string.download_noti_not_mounted;
            case ONLY_WIFI:
                return R.string.download_noti_only_wifi;
            case SUCCESSFUL:
                return R.string.download_noti_complete;
            case CANCELLED:
                return R.string.download_noti_canceled;
        }

        return -1;
    }

}
