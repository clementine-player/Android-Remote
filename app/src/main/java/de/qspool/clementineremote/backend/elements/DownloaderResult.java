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

import android.content.Context;

import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ReasonDisconnect;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseDisconnect;
import de.qspool.clementineremote.utils.RetryWait;

public class DownloaderResult extends ClementineElement {

    public enum DownloadResult {SUCCESSFUL, INSUFFIANT_SPACE, NOT_MOUNTED, CONNECTION_ERROR, FOBIDDEN, NOT_LOCAL_NETWORK, TOO_MANY_WRONG_AUTH_CODES, ONLY_WIFI, CANCELLED, ERROR}

    private DownloadResult mResult;

    private int mId;

    /** Why Clementine closed the download, if it did. */
    private ResponseDisconnect mDisconnect = ResponseDisconnect.getDefaultInstance();

    public DownloaderResult(int id, DownloadResult result) {
        mId = id;
        mResult = result;
    }

    /** Clementine closed download [id] with [disconnect]. */
    public DownloaderResult(int id, ClementineMessage disconnect) {
        this(id, refusal(disconnect));
        mDisconnect = disconnect.getMessage().getResponseDisconnect();
    }

    /**
     * Why Clementine closed a download with [disconnect]: it isn't on Clementine's local network,
     * too many wrong auth codes came from this phone, or else downloads aren't allowed.
     */
    public static DownloadResult refusal(ClementineMessage disconnect) {
        ReasonDisconnect reason = disconnect.getMessage().getResponseDisconnect().getReasonDisconnect();
        if (reason == ReasonDisconnect.Not_Local_Network) {
            return DownloadResult.NOT_LOCAL_NETWORK;
        }
        if (reason == ReasonDisconnect.Too_Many_Wrong_Auth_Codes) {
            return DownloadResult.TOO_MANY_WRONG_AUTH_CODES;
        }
        return DownloadResult.FOBIDDEN;
    }

    /** What happened, for people. */
    public String getMessage(Context context) {
        if (mResult == DownloadResult.TOO_MANY_WRONG_AUTH_CODES) {
            return context.getString(R.string.download_noti_too_many_wrong_auth_codes,
                    RetryWait.describe(context.getResources(), mDisconnect));
        }
        return context.getString(getMessageStringId());
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
            case NOT_LOCAL_NETWORK:
                return R.string.download_noti_not_local_network;
            case TOO_MANY_WRONG_AUTH_CODES:
                return R.string.download_noti_too_many_wrong_auth_codes;
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
