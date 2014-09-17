package de.qspool.clementineremote.ui.adapter;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.view.ViewGroup;

import java.util.ArrayList;

import de.qspool.clementineremote.ui.fragments.AbstractDrawerFragment;

/**
 * Created by amu on 08.03.14.
 */
public class PlayerPageAdapter extends FragmentPagerAdapter {

    private ArrayList<AbstractDrawerFragment> fragments = new ArrayList<>();

    public PlayerPageAdapter(FragmentManager fragmentManager) {
        super(fragmentManager);
    }

    @Override
    public AbstractDrawerFragment getItem(int i) {
        return fragments.get(i);
    }

    @Override
    public void destroyItem(ViewGroup viewPager, int position, Object object) {
        if (position >= getCount()) {
            FragmentManager manager = ((Fragment) object).getFragmentManager();
            FragmentTransaction trans = manager.beginTransaction();
            trans.remove((Fragment) object);
            trans.commit();
        }
    }

    @Override
    public int getCount() {
        return fragments.size();
    }

    public void addFragment(AbstractDrawerFragment fragment) {
        fragments.add(fragment);
    }
}
