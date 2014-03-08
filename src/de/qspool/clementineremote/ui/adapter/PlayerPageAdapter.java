package de.qspool.clementineremote.ui.adapter;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import de.qspool.clementineremote.ui.fragments.AbstractDrawerFragment;

import java.util.ArrayList;

/**
 * Created by amu on 08.03.14.
 */
public class PlayerPageAdapter extends FragmentPagerAdapter {
    private ArrayList<AbstractDrawerFragment> fragments = new ArrayList<>();

    public PlayerPageAdapter(FragmentManager fragmentManager) {
        super(fragmentManager);
    }

    @Override
    public Fragment getItem(int i) {
        return fragments.get(i);
    }

    @Override
    public int getCount() {
        return fragments.size();
    }

    public void addFragment(AbstractDrawerFragment fragment) {
        fragments.add(fragment);
    }
}
