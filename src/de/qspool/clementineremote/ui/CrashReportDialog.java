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

package de.qspool.clementineremote.ui;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class CrashReportDialog {
	private Context mContext;
	private String mLastTraceFileName;
	private String mLastSentTraceFileName;
	private SharedPreferences mSharedPref;

	public CrashReportDialog(Context context) {
		mContext = context;
		mSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
		mLastTraceFileName = App.mClementineExceptionHandler.getLastStracktraceFile();
		mLastSentTraceFileName = mSharedPref.getString(App.SP_LAST_SEND_STACKTRACE, "");
	}
	
	/**
	 * Did the app crash last time?
	 * @return true if it crashed and a dialog will show on showDialogIfTraceExists.
	 */
	public boolean hasTrace() {
		return !(mLastTraceFileName.equals(mLastSentTraceFileName));
	}

	public void showDialogIfTraceExists() {
		if (!hasTrace())
			return;

        AlertDialog.Builder builder;
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            builder = new AlertDialog.Builder(mContext, AlertDialog.THEME_HOLO_LIGHT);
        } else {
            builder = new AlertDialog.Builder(mContext);
        }
		
		DialogInterface.OnClickListener dialogOnClickListener = new DialogInterface.OnClickListener() {
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
		        switch (which){
		        case DialogInterface.BUTTON_POSITIVE:
		            SendMail();
		            break;

		        case DialogInterface.BUTTON_NEGATIVE:
		            break;
		        }
		    }
		};

		builder.setTitle(R.string.crash_report_title);
		builder.setMessage(R.string.crash_report_message);
		builder.setPositiveButton(R.string.crash_report_send, dialogOnClickListener);
		builder.setNegativeButton(R.string.dialog_close, dialogOnClickListener);
		
		builder.show();
		
		// Save the latest send file (even if it was not send)
		SharedPreferences.Editor edit = mSharedPref.edit();
		edit.putString(App.SP_LAST_SEND_STACKTRACE, mLastTraceFileName);
		edit.commit();
	}
	
	private void SendMail() {
		String body = "";
		File f = new File(mLastTraceFileName);
		try {
			FileReader reader = new FileReader(f);
			char[] chars = new char[(int) f.length()];
			reader.read(chars);
			body = new String(chars);
			reader.close();
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		} 
		
		Intent mailIntent = new Intent(Intent.ACTION_SEND);
        mailIntent.setType("message/rfc822");
        mailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {"asfa194@gmail.com"});
        mailIntent.putExtra(Intent.EXTRA_SUBJECT, "New Crashreport from Clementine Remote");
        mailIntent.putExtra(Intent.EXTRA_TEXT, body);
        mContext.startActivity(Intent.createChooser(mailIntent, "Send email..."));
	}
}
