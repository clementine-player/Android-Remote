package de.qspool.clementineremote.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Created by amu on 03.05.14.
 */
public class ShowcaseStore {

    public final static int SC_CONNECTDIALOG = 0b1;

    public final static int SC_PLAYER = 0b10;

    private final String SHOWCASE_KEY = "showcase";

    private SharedPreferences mSharedPref;

    private int mShowcaseStore;

    public ShowcaseStore(Context context) {
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(context);

        mShowcaseStore = mSharedPref.getInt(SHOWCASE_KEY, 0);
    }

    public boolean showShowcase(int which) {
        return ((mShowcaseStore & which) == 0);
    }

    public void setShowcaseShown(int which) {
        mShowcaseStore = mShowcaseStore | which;
        saveStore();
    }

    public void resetShowcaseStore() {
        mShowcaseStore = 0;
        saveStore();
    }

    private void saveStore() {
        SharedPreferences.Editor editor = mSharedPref.edit();
        editor.putInt(SHOWCASE_KEY, mShowcaseStore);
        editor.commit();
    }

}
