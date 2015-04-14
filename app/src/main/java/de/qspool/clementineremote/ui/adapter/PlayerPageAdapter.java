package de.qspool.clementineremote.ui.adapter;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.support.v13.app.FragmentPagerAdapter;
import android.view.ViewGroup;

import java.util.ArrayList;

public class PlayerPageAdapter extends FragmentPagerAdapter {

    private ArrayList<android.app.Fragment> fragments = new ArrayList<>();

    public PlayerPageAdapter(FragmentManager fragmentManager) {
        super(fragmentManager);
    }

    @Override
    public android.app.Fragment getItem(int i) {
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

    public void addFragment(Fragment fragment) {
        fragments.add(fragment);
    }
}
