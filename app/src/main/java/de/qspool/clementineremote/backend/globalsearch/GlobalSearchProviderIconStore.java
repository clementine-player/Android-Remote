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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalSearchProviderIconStore {
    // ConcurrentHashMap serves concurrent UI reads and forbids null keys or values.
    private final Map<String, Bitmap> mProvider = new ConcurrentHashMap<>();

    public void insertProvider(String name, ByteString buf) {
        if (name == null || buf == null || buf.isEmpty()) {
            return;
        }

        byte[] b = buf.toByteArray();
        Bitmap icon = BitmapFactory.decodeByteArray(b, 0, b.length);
        // decodeByteArray returns null on corrupt icon bytes; skip put() so ConcurrentHashMap won't throw.
        if (icon != null) {
            mProvider.put(name, icon);
        }
    }

    public Bitmap getProviderIcon(String name) {
        return name != null ? mProvider.get(name) : null;
    }
}
