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

package de.qspool.clementineremote;

import android.app.Application;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.ClementineConnection;

public class App extends Application {
	public static ClementineConnection mClementineConnection = null;
	public static Clementine mClementine = new Clementine();
	public static Application mApp;
	
	public final static int NOTIFY_ID = 78923748;
	public final static String SERVICE_ID = "ServiceIntentId";
	public final static String SERVICE_DISCONNECT_DATA = "ServiceIntentData";
	public final static int SERVICE_START = 1;
	public final static int SERVICE_CONNECTED = 2;
	public final static int SERVICE_DISCONNECTED = 3;
		
	// Keys for SharedPrefences
	public final static String SP_KEY_IP = "save_clementine_ip";
	public final static String SP_KEY_AC = "pref_autoconnect";
	public final static String SP_KEY_USE_VOLUMEKEYS = "pref_volumekey";
	public final static String SP_KEY_PORT = "pref_port";
	public final static String SP_LAST_AUTH_CODE = "last_auth_code";
	public final static String SP_USE_PEBBLE = "pref_use_pebble";
	public final static String SP_SHOW_TRACKNO = "pref_show_trackno";
}
