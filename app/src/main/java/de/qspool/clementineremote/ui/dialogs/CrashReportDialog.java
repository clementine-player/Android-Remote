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

package de.qspool.clementineremote.ui.dialogs;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import de.qspool.clementineremote.ClementineExceptionHandler;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.SharedPreferencesKeys;

public class CrashReportDialog {

    private Context mContext;

    private String mLastTraceFileName;

    private String mLastSentTraceFileName;

    private SharedPreferences mSharedPref;

    private ClementineExceptionHandler mClementineExceptionHandler;

    public CrashReportDialog(Context context) {
        mContext = context;
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        mClementineExceptionHandler = new ClementineExceptionHandler(context);

        mLastTraceFileName = mClementineExceptionHandler.getLastStracktraceFile();
        mLastSentTraceFileName = mSharedPref
                .getString(SharedPreferencesKeys.SP_LAST_SEND_STACKTRACE, "");
    }

    /**
     * Did the app crash last time?
     *
     * @return true if it crashed and a dialog will show on showDialogIfTraceExists.
     */
    public boolean hasTrace() {
        return !(mLastTraceFileName.equals(mLastSentTraceFileName));
    }

    public void showDialogIfTraceExists() {
        if (!hasTrace()) {
            return;
        }

        MaterialDialog.Builder builder = new MaterialDialog.Builder(mContext);

        builder.title(R.string.crash_report_title);
        builder.content(R.string.crash_report_message);
        builder.positiveText(R.string.crash_report_send);
        builder.negativeText(R.string.dialog_close);
        builder.onPositive(new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(MaterialDialog dialog, DialogAction which) {
                SendMail();
            }
        });

        builder.show();

        // Save the latest send file (even if it was not send)
        SharedPreferences.Editor edit = mSharedPref.edit();
        edit.putString(SharedPreferencesKeys.SP_LAST_SEND_STACKTRACE, mLastTraceFileName);
        edit.apply();
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
        } catch (IOException ignored) {
        }

        String clementineVersion = mContext.getString(R.string.clementine_version);
        try {
            PackageInfo pInfo = mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0);
            clementineVersion = pInfo.versionName + " - " + pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        Intent mailIntent = new Intent(Intent.ACTION_SEND);
        mailIntent.setType("message/rfc822");
        mailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"asfa194+ClementineRemoteCrash@gmail.com"});
        mailIntent.putExtra(Intent.EXTRA_SUBJECT, "New Crashreport from Clementine Remote " + clementineVersion);
        mailIntent.putExtra(Intent.EXTRA_TEXT, body);
        mContext.startActivity(Intent.createChooser(mailIntent, "Send email..."));
    }
}
