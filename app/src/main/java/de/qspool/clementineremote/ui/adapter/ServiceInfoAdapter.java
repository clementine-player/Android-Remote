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

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import javax.jmdns.ServiceInfo;

/**
 * Class is used for displaying service info
 */
public class ServiceInfoAdapter extends RecyclerView.Adapter<ServiceInfoAdapter.ViewHolder> {
    private List<ServiceInfo> mData;
    private ItemClickListener mListener;

    public ServiceInfoAdapter(@NonNull List<ServiceInfo> data) {
        mData = data;
    }

    public void setListener(ItemClickListener listener) {
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final ServiceInfo current = mData.get(position);
        holder.textViewHost.setText(current.getName());
        holder.textViewIp.setText(
                current.getInet4Addresses()[0].toString().split("/")[1] + ":" + current.getPort());
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onItemClick(current);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textViewHost;
        final TextView textViewIp;

        ViewHolder(View itemView) {
            super(itemView);
            textViewHost = (TextView) itemView.findViewById(android.R.id.text1);
            textViewIp = (TextView) itemView.findViewById(android.R.id.text2);
        }
    }

    public interface ItemClickListener {
        void onItemClick(ServiceInfo serviceInfo);
    }
}
