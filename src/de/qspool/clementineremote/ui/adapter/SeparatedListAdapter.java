/* This file is part of the Android Clementine Remote.
 * Copyright (C) 2013, Andreas Muttscheller <asfa194@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package de.qspool.clementineremote.ui.adapter;

import java.util.LinkedHashMap;
import java.util.Map;

import de.qspool.clementineremote.R;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;

public class SeparatedListAdapter extends BaseAdapter
	{
		public final Map<String, Adapter> sections = new LinkedHashMap<String, Adapter>();
		public final ArrayAdapter<String> headers;
		public final static int TYPE_SECTION_HEADER = 0;

		public SeparatedListAdapter(Context context)
			{
				headers = new ArrayAdapter<String>(context, R.layout.drawer_list_header);
			}

		public void addSection(String section, Adapter adapter)
			{
				this.headers.add(section);
				this.sections.put(section, adapter);
			}

		public Object getItem(int position)
			{
				for (Object section : this.sections.keySet())
					{
						Adapter adapter = sections.get(section);
						int size = adapter.getCount() + 1;

						// check if position inside this section
						if (position == 0) return section;
						if (position < size) return adapter.getItem(position - 1);

						// otherwise jump into next section
						position -= size;
					}
				return null;
			}

		public int getCount()
			{
				// total together all sections, plus one for each section header
				int total = 0;
				for (Adapter adapter : this.sections.values())
					total += adapter.getCount() + 1;
				return total;
			}

		@Override
		public int getViewTypeCount()
			{
				// assume that headers count as one, then total all sections
				int total = 1;
				for (Adapter adapter : this.sections.values())
					total += adapter.getViewTypeCount();
				return total;
			}

		@Override
		public int getItemViewType(int position)
			{
				int type = 1;
				for (Object section : this.sections.keySet())
					{
						Adapter adapter = sections.get(section);
						int size = adapter.getCount() + 1;

						// check if position inside this section
						if (position == 0) return TYPE_SECTION_HEADER;
						if (position < size) return type + adapter.getItemViewType(position - 1);

						// otherwise jump into next section
						position -= size;
						type += adapter.getViewTypeCount();
					}
				return -1;
			}

		public boolean areAllItemsSelectable()
			{
				return false;
			}

		@Override
		public boolean isEnabled(int position)
			{
				return (getItemViewType(position) != TYPE_SECTION_HEADER);
			}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
			{
				int sectionnum = 0;
				for (Object section : this.sections.keySet())
					{
						Adapter adapter = sections.get(section);
						int size = adapter.getCount() + 1;

						// check if position inside this section
						if (position == 0) return headers.getView(sectionnum, convertView, parent);
						if (position < size) return adapter.getView(position - 1, convertView, parent);

						// otherwise jump into next section
						position -= size;
						sectionnum++;
					}
				return null;
			}

		@Override
		public long getItemId(int position)
			{
				return position;
			}

	}