package de.qspool.clementineremote.utils;

import android.annotation.SuppressLint;

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
}
