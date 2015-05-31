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

package de.qspool.clementineremote.backend.globalsearch;

import com.google.protobuf.ByteString;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.HashMap;

public class GlobalSearchProviderIconStore {
    private HashMap<String, Bitmap> mProvider = new HashMap<>();

    public void insertProvider(String name, ByteString buf) {
        if (buf == null || buf.size() == 0) {
            return;
        }

        byte[] b = buf.toByteArray();
        Bitmap icon = BitmapFactory.decodeByteArray(b, 0, b.length);
        mProvider.put(name, icon);
    }

    public Bitmap getProviderIcon(String name) {
        return mProvider.get(name);
    }
}
