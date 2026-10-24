package de.qspool.clementineremote.ui.shell

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Screens lay out around a fold only when it splits them, measured from their own edges. */
class FoldTest {
    private val density = Density(2f)

    // A book-style foldable's inner screen, 2000 × 1600 pixels, with the player below a top row.
    private val player = Rect(0f, 100f, 2000f, 1600f)

    private fun hinge(bounds: Rect, vertical: Boolean, separating: Boolean = true) =
        HingeInfo(bounds, isFlat = !separating, isVertical = vertical, isSeparating = separating, isOccluding = false)

    private fun fold(vararg hinges: HingeInfo, direction: LayoutDirection = LayoutDirection.Ltr) =
        foldAcross(Posture(hingeList = hinges.toList()), player, density, direction)

    @Test
    fun noFoldOnAnOrdinaryPhone() {
        assertNull(fold())
    }

    @Test
    fun aFoldOpenFlatSplitsNothing() {
        assertNull(fold(hinge(Rect(1000f, 0f, 1000f, 1600f), vertical = true, separating = false)))
    }

    @Test
    fun halfOpenLikeABook() {
        assertEquals(Fold.Vertical(500.dp, 0.dp), fold(hinge(Rect(1000f, 0f, 1000f, 1600f), vertical = true)))
    }

    @Test
    fun theLeadingSideIsOnTheRightInRightToLeft() {
        val fold = fold(hinge(Rect(800f, 0f, 840f, 1600f), vertical = true), direction = LayoutDirection.Rtl)
        assertEquals(Fold.Vertical(580.dp, 20.dp), fold)
    }

    @Test
    fun setDownLikeALaptopFromTheLayoutsTop() {
        assertEquals(Fold.Horizontal(350.dp, 0.dp), fold(hinge(Rect(0f, 800f, 2000f, 800f), vertical = false)))
    }

    @Test
    fun aFoldOutsideTheLayoutIsIgnored() {
        // Above the player, in its top row.
        assertNull(fold(hinge(Rect(0f, 50f, 2000f, 50f), vertical = false)))
    }
}
