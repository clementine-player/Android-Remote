package de.qspool.clementineremote.ui.adapter;

import android.content.Context;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentTransaction;

import java.util.ArrayList;

import de.qspool.clementineremote.ui.interfaces.NameableTitle;

public class PlayerPageAdapter extends FragmentPagerAdapter {

    private Context mContext;

    private ArrayList<Fragment> fragments = new ArrayList<>();

    public PlayerPageAdapter(Context context, FragmentManager fragmentManager) {
        // As the legacy adapter did: every page the pager holds is resumed.
        super(fragmentManager, BEHAVIOR_SET_USER_VISIBLE_HINT);
        mContext = context;
    }

    @Override
    public Fragment getItem(int i) {
        return fragments.get(i);
    }

    @Override
    public void destroyItem(ViewGroup viewPager, int position, Object object) {
        if (position >= getCount()) {
            FragmentManager manager = ((Fragment) object).getParentFragmentManager();
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
