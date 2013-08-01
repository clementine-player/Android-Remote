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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class ClementineExceptionHandler implements UncaughtExceptionHandler {
	private Context mContext;
	private UncaughtExceptionHandler mDefaultUEH;
	private SharedPreferences mSharedPref;

	public ClementineExceptionHandler(Context context) {
		mContext = context;
		mDefaultUEH = Thread.getDefaultUncaughtExceptionHandler();
		mSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
	}

	@Override
	public void uncaughtException(Thread thread, Throwable ex) {
		// Delete the last trace file!
		removeLastTraceFile();
		
		// Open the file and delete if it already exists
		String filename = buildTraceFileName();
		File f = new File(filename);
		
		try {
			// Write the stacktrace to the file
			PrintWriter printer = new PrintWriter(f);
			ex.printStackTrace(printer);
			printer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		// Save the new filename
		SharedPreferences.Editor edit = mSharedPref.edit();
		edit.putString(App.SP_LAST_STACKTRACE, filename);
		edit.commit();
		
		// Call the default uncaught Exception handler to tell the 
		// system, that this app crashed
		mDefaultUEH.uncaughtException(thread, ex);
	}

	/**
	 * Builds the filename for the stacktrace file (/data/data/de.qspool....)
	 * @return The filename with path as a string
	 */
	private String buildTraceFileName() {
		SimpleDateFormat ft = 
			      new SimpleDateFormat ("yyyy.MM.dd-hh:mm:ss");
		StringBuilder sb = new StringBuilder();
		sb.append(mContext.getExternalCacheDir());
		sb.append("/stacktrace-");
		sb.append(ft.format(new Date()));
		sb.append(".txt");
		return sb.toString(); 
	}
	
	public String getLastStracktraceFile() {
		return mSharedPref.getString(App.SP_LAST_STACKTRACE, "");
	}
	
	public boolean removeLastTraceFile() {
		File f = new File(getLastStracktraceFile());
		return f.delete();
	}

}
