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

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.backend.requests.RequestControl;
import de.qspool.clementineremote.backend.requests.RequestVolume;
import de.qspool.clementineremote.backend.requests.RequestControl.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.view.KeyEvent;

public class ClementineMediaButtonEventReceiver extends BroadcastReceiver {
	static boolean ignoreNext;
	
	@Override
	public void onReceive(Context context, Intent intent) {
		// Check if we have an media button intent
		if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
			// TODO:
			// Intent gets send twice, don't know why.
			if (ignoreNext) {
				ignoreNext = false;
				return;
			}
			ignoreNext = true;
			
			// Get the key event and obtain a new message
			KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			Message msg = Message.obtain();
			
			// Check which key was pressed
			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
				msg.obj = new RequestControl(Request.PLAYPAUSE);
				break;
			case KeyEvent.KEYCODE_MEDIA_NEXT:
				msg.obj = new RequestControl(Request.NEXT);
				break;
			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
				msg.obj = new RequestControl(Request.PREV);
				break;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				msg.obj = new RequestVolume(App.mClementine.getVolume() - 10);
				break;
			case KeyEvent.KEYCODE_VOLUME_UP:
				msg.obj = new RequestVolume(App.mClementine.getVolume() + 10);
				break;
			default:
				msg = null;
				break;
			}
			
			// Now send the message
			if (msg != null
			 && App.mClementineConnection != null) {
				App.mClementineConnection.mHandler.sendMessage(msg);
			}
		}
	}

}
