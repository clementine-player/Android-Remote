package de.qspool.clementineremote.ui.adapter;

import android.content.Context;
import android.preference.PreferenceActivity.Header;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import de.qspool.clementineremote.R;

public class PreferenceHeaderAdapter extends ArrayAdapter<Header> {

    private LayoutInflater mLayoutInflater;

    public PreferenceHeaderAdapter(Context context, List<Header> items) {
        super(context, 0, items);

        mLayoutInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Header header = getItem(position);
        View view;

        if (isHeaderCategory(header)) {
            view = mLayoutInflater.inflate(android.R.layout.preference_category, parent, false);
            ((TextView) view.findViewById(android.R.id.title))
                    .setText(header.getTitle(getContext().getResources()));
        } else {
            view = mLayoutInflater.inflate(R.layout.preference_header_item, parent, false);
            ((ImageView) view.findViewById(R.id.pref_header_icon)).setImageResource(header.iconRes);
            ((TextView) view.findViewById(R.id.pref_header_title))
                    .setText(header.getTitle(getContext().getResources()));
            ((TextView) view.findViewById(R.id.pref_header_summary)).setText(header.getSummary(
                    getContext().getResources()));
        }

        return view;
    }

    @Override
    public boolean isEnabled(int position) {
        return !isHeaderCategory(getItem(position));
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

    public static boolean isHeaderCategory(Header header) {
        return (header.fragment == null && header.intent == null);
    }
}
