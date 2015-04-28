package de.qspool.clementineremote.backend.library.groupings;

import android.content.Context;
import android.database.Cursor;

import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.library.LibraryAlbumOrder;
import de.qspool.clementineremote.backend.library.LibraryGroup;
import de.qspool.clementineremote.backend.library.LibrarySelectItem;

public class LibraryGroupArtistAlbum extends LibraryGroup {

    // Field indicies
    private final int IDX_ID = 0;

    private final int IDX_ARTIST = 1;

    private final int IDX_ALBUM = 2;

    private final int IDX_TITLE = 3;

    private final int IDX_URL = 4;

    private final int IDX_YEAR = 5;

    public LibraryGroupArtistAlbum(Context context) {
        super(context);
    }

    @Override
    public int getMaxLevels() {
        return 3;
    }

    @Override
    public Cursor buildQuery(String fromTable) {
        Cursor c1 = null;
        String query = "SELECT ROWID as _id, artist, album, title, cast(filename as TEXT), year FROM " + fromTable + " ";
        try {
            switch (mLevel) {
                case 0:
                    query += "group by artist order by artist";
                    c1 = mDatabase.rawQuery(query, null);
                    break;
                case 1:
                    query += "WHERE artist = ? group by album order by ";
                    query += LibraryAlbumOrder.RELEASE.equals(mLibraryAlbumOrder) ? "year, " : "";
                    query += "album";
                    c1 = mDatabase.rawQuery(query, mSelection);
                    break;
                case 2:
                    if (mSelection.length < 2 || mSelection[1].length() == 0) {
                        query += "WHERE artist = ? order by ";
                        query += LibraryAlbumOrder.RELEASE.equals(mLibraryAlbumOrder) ? "year, " : "";
                        query += "album, disc, track";
                        c1 = mDatabase.rawQuery(query, mSelection);
                    } else {
                        query += "WHERE artist = ? and album = ? order by disc, track";
                        c1 = mDatabase.rawQuery(query, mSelection);
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            System.out.println("DATABASE ERROR " + e);

        }
        return c1;
    }

    protected int countItems(String[] selection) {
        int items = 0;
        Cursor c = null;

        switch (mLevel) {
            case 0:
                c = mDatabase
                        .rawQuery(
                                "SELECT count(distinct(album)) FROM songs where artist = ?",
                                selection);
                break;
            case 1:
                c = mDatabase
                        .rawQuery(
                                "SELECT count(title) FROM songs where artist = ? and album = ?",
                                selection);
                break;
        }

        if (c != null && c.moveToFirst()) {
            items = c.getInt(0);
            c.close();
        }

        return items;
    }

    @Override
    public LibrarySelectItem fillLibrarySelectItem(Cursor c) {
        LibrarySelectItem item = new LibrarySelectItem();
        String unknownItem = mContext.getString(R.string.library_unknown_item);

        String artist = c.getString(IDX_ARTIST);
        String album = c.getString(IDX_ALBUM);
        String title = c.getString(IDX_TITLE);
        int year = c.getInt(IDX_YEAR);

        switch (mLevel) {
            case 0:
                item.setSelection(new String[] {artist});
                if (artist.isEmpty()) {
                    item.setListTitle(unknownItem);
                } else {
                    item.setListTitle(artist);
                }
                item.setListSubtitle(String.format(
                        mContext.getString(R.string.library_no_albums),
                        countItems(item.getSelection())));
                break;
            case 1:
                item.setSelection(new String[] {artist, album});

                if (album.isEmpty()) {
                    item.setListTitle(unknownItem);
                } else {
                    item.setListTitle((mLibraryAlbumOrder == LibraryAlbumOrder.ALPHABET ? "" : (year + " - ")) + album);
                }
                item.setListSubtitle(String.format(
                        mContext.getString(R.string.library_no_tracks),
                        countItems(item.getSelection())));
                break;
            case 2:
                item.setSelection(new String[] {artist, album, title});
                item.setUrl(c.getString(IDX_URL));

                if (artist.isEmpty()) {
                    item.setListTitle(item.getUrl().substring(item.getUrl().lastIndexOf("/") + 1));
                } else {
                    item.setListTitle(title);
                }

                item.setListSubtitle((artist.isEmpty() ? unknownItem : artist)
                        + " / " + (album.isEmpty() ? unknownItem : album));
                break;
            default:
                break;
        }
        item.setLevel(mLevel);

        return item;
    }
}
