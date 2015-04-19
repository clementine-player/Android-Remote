package de.qspool.clementineremote.ui.adapter;

import android.app.Fragment;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import de.qspool.clementineremote.R;

public class PreferenceHeaderAdapter extends ArrayAdapter<PreferenceHeaderAdapter.PreferenceHeader> {

    public static class PreferenceHeader {
        public String title;
        public String summary;
        public int icon;

        public Fragment fragment;
        public boolean isHeader;

        public PreferenceHeader(String title, String summary, int icon, Fragment fragment) {
            this.title = title;
            this.summary = summary;
            this.icon = icon;
            this.fragment = fragment;
            this.isHeader = (fragment == null);
        }

        public PreferenceHeader(String title) {
            this.title = title;
            this.isHeader = true;
        }
    }

    private LayoutInflater mLayoutInflater;

    public PreferenceHeaderAdapter(Context context, List<PreferenceHeader> items) {
        super(context, 0, items);

        mLayoutInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        PreferenceHeader header = getItem(position);

        if (header.isHeader) {
            view = mLayoutInflater.inflate(android.R.layout.preference_category, parent, false);
            ((TextView) view.findViewById(android.R.id.title)).setText(header.title);
        } else {
            view = mLayoutInflater.inflate(R.layout.preference_header_item, parent, false);
            ((ImageView) view.findViewById(R.id.pref_header_icon)).setImageResource(header.icon);
            ((TextView) view.findViewById(R.id.pref_header_title))
                    .setText(header.title);
            ((TextView) view.findViewById(R.id.pref_header_summary)).setText(header.summary);
        }

        return view;
    }

    @Override
    public boolean isEnabled(int position) {
        return !getItem(position).isHeader;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public int getViewTypeCount() {
        return super.getViewTypeCount();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}
