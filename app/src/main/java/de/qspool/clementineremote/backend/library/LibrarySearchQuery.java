package de.qspool.clementineremote.backend.library;

import android.content.Context;

import de.qspool.clementineremote.ui.search.SearchSectionsKt;

/**
 * The library, browsed as its search results open: by album artist, then album and song,
 * whatever the library's grouping.
 */
public class LibrarySearchQuery extends LibraryQuery {

    public LibrarySearchQuery(Context context) {
        super(context);
    }

    @Override
    protected String[] getSelectedFields() {
        return new String[] {SearchSectionsKt.GROUP_ARTIST, "album", "title"};
    }
}
