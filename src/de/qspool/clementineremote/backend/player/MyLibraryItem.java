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

package de.qspool.clementineremote.backend.player;

import java.io.File;

import de.qspool.clementineremote.App;
import android.database.sqlite.SQLiteDatabase;

public class MyLibraryItem {
	private String mTitle = new String();
	private String mSubtitle = new String();
	
	public String getTitle() {
		return mTitle;
	}
	public void setTitle(String mTitle) {
		this.mTitle = mTitle;
	}
	public String getSubtitle() {
		return mSubtitle;
	}
	public void setSubtitle(String mSubtitle) {
		this.mSubtitle = mSubtitle;
	}
	
}
