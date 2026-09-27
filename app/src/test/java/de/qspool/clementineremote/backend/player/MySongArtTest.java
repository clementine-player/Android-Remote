package de.qspool.clementineremote.backend.player;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.protobuf.ByteString;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;

import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SongMetadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Cover art is decoded once when set, and compared by its bytes rather than pixels. */
@RunWith(RobolectricTestRunner.class)
public class MySongArtTest {

    private static ByteString png(int color) {
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        return ByteString.copyFrom(out.toByteArray());
    }

    private static MySong songWithArt(ByteString art) {
        MySong song = new MySong();
        if (art != null) {
            song.setArt(art);
        }
        return song;
    }

    @Test
    public void artIsDecodedOnceAndCached() {
        MySong song = songWithArt(png(Color.RED));
        Bitmap art = song.getArt();
        assertNotNull(art);
        assertEquals(4, art.getWidth());
        assertSame(art, song.getArt());
    }

    @Test
    public void sameBytesMeanSameArt() {
        assertTrue(songWithArt(png(Color.RED)).hasSameArt(songWithArt(png(Color.RED))));
    }

    @Test
    public void differentBytesMeanDifferentArt() {
        assertFalse(songWithArt(png(Color.RED)).hasSameArt(songWithArt(png(Color.BLUE))));
    }

    @Test
    public void songsWithoutArtHaveTheSameArt() {
        assertTrue(songWithArt(null).hasSameArt(songWithArt(null)));
        assertFalse(songWithArt(png(Color.RED)).hasSameArt(songWithArt(null)));
        assertFalse(songWithArt(null).hasSameArt(null));
    }

    @Test
    public void undecodableArtFallsBackToTheNoCoverImage() {
        MySong song = songWithArt(ByteString.copyFromUtf8("not an image"));
        assertNotNull(song.getArt());
    }

    private static MySong fromClementine(Boolean coverFields, String automatic, String manual) {
        SongMetadata.Builder metadata = SongMetadata.newBuilder().setTitle("Clair de lune").setArt(png(Color.GRAY));
        if (coverFields) {
            metadata.setArtAutomatic(automatic).setArtManual(manual);
        }
        return MySong.fromProtocolBuffer(metadata.build());
    }

    @Test
    public void clementinesNoCoverPictureIsNotTakenForTheCover() {
        // Clementine sends its "no cover" picture for a song whose cover fields are empty,
        // or whose cover was removed by hand.
        assertNull(fromClementine(true, "", "").getArtData());
        assertNull(fromClementine(true, "/music/cover.jpg", "(unset)").getArtData());
    }

    @Test
    public void aCoverFromAnyWhereIsKept() {
        assertNotNull(fromClementine(true, "/music/Suite bergamasque/cover.jpg", "").getArtData());
        assertNotNull(fromClementine(true, "(embedded)", "").getArtData());
        assertNotNull(fromClementine(true, "", "/home/me/covers/debussy.jpg").getArtData());
        // Without the cover fields, there's no telling: the art is kept.
        assertNotNull(fromClementine(false, null, null).getArtData());
    }
}
