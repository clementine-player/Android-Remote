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

package de.qspool.clementineremote.backend.library;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class LibrarySelectItem {

    private int mLevel;

    private String[] mSelection;

    private String mUrl = "";

    private String mListTitle = "";

    private String mListSubtitle = "";

    public int getLevel() {
        return mLevel;
    }

    public void setLevel(int level) {
        mLevel = level;
    }

    public String[] getSelection() {
        return mSelection;
    }

    public void setSelection(String[] selection) {
        mSelection = selection;
    }

    public String getUrl() {
        return mUrl;
    }

    public void setUrl(String mUrl) {
        try {
            this.mUrl = URLDecoder.decode(mUrl.replace("+", "%2B"), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            this.mUrl = mUrl;
        }
    }

    public String getListTitle() {
        return mListTitle;
    }

    public void setListTitle(String listTitle) {
        mListTitle = listTitle;
    }

    public String getListSubtitle() {
        return mListSubtitle;
    }

    public void setListSubtitle(String subtitle) {
        mListSubtitle = subtitle;
    }
}
