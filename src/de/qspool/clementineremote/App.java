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

public class App {
	public static ClementineConnection mClementineConnection = null;
	public static Clementine mClementine = null;
	public static Application mApp;
	
	// Keys for SharedPrefences
	public final static String SP_KEY_IP = "save_clementine_ip";
	public final static String SP_KEY_AC = "pref_autoconnect";
	public final static String SP_KEY_USE_VOLUMEKEYS = "pref_volumekey";
	public final static String SP_KEY_PORT = "pref_port";
	
	// Other statics
	public final static int PROTOCOL_BUFFER_VERSION = 1;
}
