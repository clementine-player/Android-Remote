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

import com.google.protobuf.ByteString;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SongMetadata;

/**
 * Representation of a song
 */
public class MySong {

    private int id;

    private int index;

    private String title = "";

    private String artist = "";

    private String album = "";

    private String albumartist = "";

    private String prettyLength = "";

    private int length;

    private String genre = "";

    private String year = "";

    private int track;

    private int disc;

    private int playcount;

    private byte[] art;

    /** The decoded cover, or null if there is none or it could not be decoded. */
    private Bitmap artBitmap;

    // Shared fallback bitmap so tracks without art avoid re-decoding nocover.
    private static volatile Bitmap sNoCoverBitmap;

    private boolean loved;

    // Volatile so reference swaps publish safely; CopyOnWriteArrayList so readers can iterate while the parser appends.
    private volatile List<LyricsProvider> mLyricsProvider = new CopyOnWriteArrayList<LyricsProvider>();

    private String filename = "";

    private long size;

    private boolean local;

    private float rating;

    private String url;

    public MySong() {
    }

    public MySong(MySong other) {
        this.id = other.id;
        this.index = other.index;
        this.title = other.title;
        this.artist = other.artist;
        this.album = other.album;
        this.albumartist = other.albumartist;
        this.prettyLength = other.prettyLength;
        this.length = other.length;
        this.genre = other.genre;
        this.year = other.year;
        this.track = other.track;
        this.disc = other.disc;
        this.playcount = other.playcount;
        this.art = other.art;
        this.artBitmap = other.artBitmap;
        this.loved = other.loved;
        this.mLyricsProvider = new CopyOnWriteArrayList<LyricsProvider>(other.mLyricsProvider);
        this.filename = other.filename;
        this.size = other.size;
        this.local = other.local;
        this.rating = other.rating;
        this.url = other.url;
    }

    /**
     * Compares song metadata and cover art bytes explicitly without overriding
     * {@link Object#equals}, which {@code RemoteRepository.NowPlaying} relies on
     * to distinguish new {@link MySong} instances by reference identity.
     */
    public boolean sameMetadata(@Nullable MySong song) {
        if (this == song) {
            return true;
        }
        if (song == null) {
            return false;
        }
        return this.id == song.id
                && this.index == song.index
                && this.length == song.length
                && this.track == song.track
                && this.disc == song.disc
                && this.playcount == song.playcount
                && this.loved == song.loved
                && this.local == song.local
                && this.size == song.size
                && Float.compare(song.rating, this.rating) == 0
                && Objects.equals(this.artist, song.artist)
                && Objects.equals(this.title, song.title)
                && Objects.equals(this.album, song.album)
                && Objects.equals(this.albumartist, song.albumartist)
                && Objects.equals(this.prettyLength, song.prettyLength)
                && Objects.equals(this.genre, song.genre)
                && Objects.equals(this.year, song.year)
                && Objects.equals(this.filename, song.filename)
                && Objects.equals(this.url, song.url)
                && Arrays.equals(this.art, song.art);
    }

    @SuppressLint("DefaultLocale")
    public boolean contains(String constraint) {
        String cs = constraint.toLowerCase();
        return (title.toLowerCase().contains(cs) ||
                artist.toLowerCase().contains(cs) ||
                album.toLowerCase().contains(cs) ||
                albumartist.toLowerCase().contains(cs) ||
                genre.toLowerCase().contains(cs) ||
                year.toLowerCase().contains(cs));
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(artist);
        sb.append(" - ");
        sb.append(title);
        return sb.toString();
    }

    public static MySong fromProtocolBuffer(SongMetadata songMetadata) {
        MySong song = new MySong();

        // Apply the metadata
        song.setId(songMetadata.getId());
        song.setIndex(songMetadata.getIndex());
        song.setArtist(songMetadata.getArtist());
        song.setTitle(songMetadata.getTitle());
        song.setAlbum(songMetadata.getAlbum());
        song.setAlbumartist(songMetadata.getAlbumartist());
        song.setPrettyLength(songMetadata.getPrettyLength());
        song.setLength(songMetadata.getLength());
        song.setGenre(songMetadata.getGenre());
        song.setYear(songMetadata.getPrettyYear());
        song.setTrack(songMetadata.getTrack());
        song.setDisc(songMetadata.getDisc());
        song.setPlaycount(songMetadata.getPlaycount());
        song.setFilename(songMetadata.getFilename());
        song.setSize(songMetadata.getFileSize());
        song.setLocal(songMetadata.getIsLocal());
        song.setRating(songMetadata.getRating());
        song.setUrl(songMetadata.getUrl());

        if (songMetadata.hasArt() && hasCover(songMetadata)) {
            song.setArt(songMetadata.getArt());
        }

        return song;
    }

    /** Clementine's art_manual for a cover the user removed. */
    private static final String MANUALLY_UNSET_COVER = "(unset)";

    /**
     * Whether the art Clementine sent is the song's cover. For a song without one, Clementine
     * sends its own "no cover" picture instead, which its cover fields give away: none set, or
     * the cover removed by hand. A Clementine that doesn't send those fields sends only covers.
     */
    private static boolean hasCover(SongMetadata songMetadata) {
        if (!songMetadata.hasArtAutomatic() && !songMetadata.hasArtManual()) {
            return true;
        }
        String manual = songMetadata.getArtManual();
        if (MANUALLY_UNSET_COVER.equals(manual)) {
            return false;
        }
        return !manual.isEmpty() || !songMetadata.getArtAutomatic().isEmpty();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getAlbumartist() {
        return albumartist;
    }

    public void setAlbumartist(String albumartist) {
        this.albumartist = albumartist;
    }

    public String getPrettyLength() {
        return prettyLength;
    }

    public void setPrettyLength(String prettyLength) {
        this.prettyLength = prettyLength;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public int getTrack() {
        return track;
    }

    public void setTrack(int track) {
        this.track = track;
    }

    public int getDisc() {
        return disc;
    }

    public void setDisc(int disc) {
        this.disc = disc;
    }

    public int getPlaycount() {
        return playcount;
    }

    public void setPlaycount(int playcount) {
        this.playcount = playcount;
    }

    /**
     * The cover art, or the "no cover" image. Cheap: the cover was decoded when the song was
     * created, on the connection thread, so the UI never decodes it.
     */
    public Bitmap getArt() {
        if (artBitmap != null) {
            return artBitmap;
        }
        Bitmap cached = sNoCoverBitmap;
        if (cached == null) {
            synchronized (MySong.class) {
                cached = sNoCoverBitmap;
                if (cached == null) {
                    cached = BitmapFactory.decodeResource(
                            App.getApp().getResources(), R.drawable.nocover);
                    sNoCoverBitmap = cached;
                }
            }
        }
        return cached;
    }

    /** The cover art as Clementine sent it (compressed), or null if the song has none. */
    @Nullable
    public byte[] getArtData() {
        return art;
    }

    /**
     * Sets and decodes the cover art. Songs with art come from Clementine's messages, which
     * are parsed on the connection or downloader thread, so decoding here keeps it off the
     * UI thread.
     */
    @WorkerThread
    public void setArt(ByteString byteString) {
        this.art = byteString.toByteArray();
        this.artBitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
    }

    /** Attaches pre-decoded art from a worker thread so UI never decodes it. */
    public void setArt(@Nullable byte[] artBytes, @Nullable Bitmap decodedBitmap) {
        this.art = artBytes;
        this.artBitmap = decodedBitmap;
    }

    /**
     * Whether both songs have the same cover art (or both have none), comparing the
     * compressed bytes Clementine sent rather than pixels.
     */
    public boolean hasSameArt(MySong other) {
        return other != null && Arrays.equals(art, other.art);
    }

    public boolean isLoved() {
        return loved;
    }

    public void setLoved(boolean loved) {
        this.loved = loved;
    }

    public List<LyricsProvider> getLyricsProvider() {
        return mLyricsProvider;
    }

    public void setLyricsProvider(Collection<LyricsProvider> providers) {
        mLyricsProvider = new CopyOnWriteArrayList<LyricsProvider>(providers);
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
