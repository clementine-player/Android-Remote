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

package de.qspool.clementineremote.backend.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.ClementinePlayerConnection;
import de.qspool.clementineremote.backend.ClementineService;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.ui.TaskerSettings;
import de.qspool.clementineremote.utils.bundle.BundleScrubber;
import de.qspool.clementineremote.utils.bundle.PluginBundleManager;

public class TaskerFireReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null
                || !de.qspool.clementineremote.utils.bundle.LocalePluginIntent.ACTION_FIRE_SETTING
                        .equals(intent.getAction())) {
            return;
        }
        // Scrub untrusted exported-broadcast extras before reading keys so
        // injected custom Parcelables cannot throw BadParcelableException.
        BundleScrubber.scrub(intent);
        final Bundle bundle = intent.getBundleExtra(
                de.qspool.clementineremote.utils.bundle.LocalePluginIntent.EXTRA_BUNDLE);
        BundleScrubber.scrub(bundle);
        if (!PluginBundleManager.isBundleValid(bundle)) {
            return;
        }

        Message msg = Message.obtain();

        switch (bundle.getInt(PluginBundleManager.BUNDLE_EXTRA_INT_TYPE)) {
            case TaskerSettings.ACTION_CONNECT:
                String ip = bundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_IP);
                int port = bundle.getInt(PluginBundleManager.BUNDLE_EXTRA_INT_PORT, -1);
                int auth = bundle.getInt(PluginBundleManager.BUNDLE_EXTRA_INT_AUTH, 0);
                // Reject empty hosts or out-of-range ports before InetSocketAddress.
                if (ip == null || ip.trim().isEmpty() || port < 1 || port > 65535) {
                    return;
                }

                // Start the background service
                Intent serviceIntent = new Intent(context, ClementineService.class);
                serviceIntent.putExtra(ClementineService.SERVICE_ID,
                        ClementineService.SERVICE_START);
                serviceIntent.putExtra(ClementineService.EXTRA_STRING_IP, ip.trim());
                serviceIntent.putExtra(ClementineService.EXTRA_INT_PORT, port);
                serviceIntent.putExtra(ClementineService.EXTRA_INT_AUTH, auth);
                ClementineService.startConnection(context, serviceIntent);
                break;
            case TaskerSettings.ACTION_DISCONNECT:
                msg.obj = ClementineMessage.getMessage(MsgType.DISCONNECT);
                break;
            case TaskerSettings.ACTION_PLAY:
                msg.obj = ClementineMessage.getMessage(MsgType.PLAY);
                break;
            case TaskerSettings.ACTION_PAUSE:
                msg.obj = ClementineMessage.getMessage(MsgType.PAUSE);
                break;
            case TaskerSettings.ACTION_PLAYPAUSE:
                msg.obj = ClementineMessage.getMessage(MsgType.PLAYPAUSE);
                break;
            case TaskerSettings.ACTION_NEXT:
                msg.obj = ClementineMessage.getMessage(MsgType.NEXT);
                break;
            case TaskerSettings.ACTION_STOP:
                msg.obj = ClementineMessage.getMessage(MsgType.STOP);
                break;
            default:
                return;
        }

        // Now send the message
        ClementinePlayerConnection connection = App.ClementineConnection;
        Handler handler = connection != null ? connection.mHandler : null;
        if (msg.obj != null && handler != null) {
            handler.sendMessage(msg);
        }
    }
}
