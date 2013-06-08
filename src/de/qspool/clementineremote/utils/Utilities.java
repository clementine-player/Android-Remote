package de.qspool.clementineremote.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;

import de.qspool.clementineremote.R;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class Utilities {
	@SuppressLint("DefaultLocale")
	public static String PrettyTime(int seconds) {
	  // last.fm sometimes gets the track length wrong, so you end up with
	  // negative times.
	  seconds = Math.abs(seconds);

	  int hours = seconds / (60*60);
	  int minutes = (seconds / 60) % 60;
	  seconds %= 60;

	  String ret = "";
	  if (hours > 0)
	    ret = String.format("%d:%02d:%02d", hours, minutes, seconds);
	  else
	    ret = String.format("%d:%02d", minutes, seconds);

	  return ret;
	}
	
	/**
	 * Show a simple designed message dialog
	 * @param context In which context will the dialog be displayed?
	 * @param title The resource id of the message dialog title.
	 * @param message The resource id of the message
	 * @return The Dialog object
	 */
	public static Dialog ShowMessageDialog(Context context, int title, int message) {
		return ShowMessageDialog(context, context.getString(title), context.getString(message), false);
	}
	
	/**
	 * Show a simple designed message dialog
	 * @param context In which context will the dialog be displayed?
	 * @param title The resource id of the message dialog title.
	 * @param message The resource id of the message
	 * @return The Dialog object
	 */
	public static Dialog ShowHtmlMessageDialog(Context context, int title, int message) {
		return ShowMessageDialog(context, context.getString(title), context.getString(message), true);
	}
	
	/**
	 * Show a simple designed message dialog
	 * @param context In which context will the dialog be displayed?
	 * @param title The message string dialog title.
	 * @param message The string of the message
	 * @return The Dialog object
	 */
	public static Dialog ShowMessageDialog(Context context, String title, String message, boolean hasHtml) {
		final Dialog errorDialog = new Dialog(context, R.style.Dialog_Transparent);
		errorDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		errorDialog.setContentView(R.layout.dialog_message);
		
		// Set the Views
		final TextView tvTitle = (TextView) errorDialog.findViewById(R.id.tvTitle);
		final TextView tvMessage = (TextView) errorDialog.findViewById(R.id.tvMessage);
		tvTitle.setText(title);
		if (hasHtml) {
			tvMessage.setText(Html.fromHtml(message));
		} else {
			tvMessage.setText(message);
		}
		tvMessage.setMovementMethod(LinkMovementMethod.getInstance());
		
		Button connectButton = (Button) errorDialog.findViewById(R.id.btnClose);
				connectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				errorDialog.cancel();
			}
	    });
				
		errorDialog.show();
		
		return errorDialog;
	}
	
	public static byte[] ToIPByteArray(int addr){
        return new byte[]{(byte)addr,(byte)(addr>>>8),(byte)(addr>>>16),(byte)(addr>>>24)};
    }

	public static InetAddress ToInetAddress(int addr){
	    try {
			return InetAddress.getByAddress(ToIPByteArray(addr));
		} catch (UnknownHostException e) {
			return null;
		}
	}
}
