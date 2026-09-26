package de.qspool.clementineremote.ui.player

import androidx.compose.ui.platform.ComposeView
import de.qspool.clementineremote.ui.ComposeViews

/**
 * The player, in Compose, for the View-based fragment that hosts it until the app's shell moves
 * to Compose.
 */
object PlayerViews {

    /** Hears which of the player's pages is shown. */
    fun interface PageListener {
        fun onPageChanged(page: Int)
    }

    /**
     * Shows the player in [view]. Tapping the artwork runs [onArtClick]; [onPageChanged] hears
     * which page is shown.
     */
    @JvmStatic
    fun showPlayer(view: ComposeView, onArtClick: Runnable, onPageChanged: PageListener) =
        ComposeViews.show(view) {
            PlayerScreen(onArtClick = onArtClick::run, onPageChanged = onPageChanged::onPageChanged)
        }
}
