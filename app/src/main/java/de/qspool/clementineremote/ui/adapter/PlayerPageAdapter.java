package de.qspool.clementineremote.ui.adapter;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.support.v13.app.FragmentPagerAdapter;
import android.view.ViewGroup;

import java.util.ArrayList;

import de.qspool.clementineremote.ui.interfaces.NameableTitle;

public class PlayerPageAdapter extends FragmentPagerAdapter {

    private Context mContext;

    private ArrayList<android.app.Fragment> fragments = new ArrayList<>();

    public PlayerPageAdapter(Context context, FragmentManager fragmentManager) {
        super(fragmentManager);
        mContext = context;
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

    @Override
    public CharSequence getPageTitle(int position) {
        int id = ((NameableTitle) fragments.get(position)).getTitleId();
        return mContext.getString(id);
    }
}
