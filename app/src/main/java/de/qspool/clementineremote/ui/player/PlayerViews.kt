package de.qspool.clementineremote.ui.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import de.qspool.clementineremote.ui.theme.ClementineTheme

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
    fun showPlayer(view: ComposeView, onArtClick: Runnable, onPageChanged: PageListener) = show(view) {
        PlayerScreen(onArtClick = onArtClick::run, onPageChanged = onPageChanged::onPageChanged)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun show(view: ComposeView, content: @Composable () -> Unit) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            // Until the app's shell moves to Compose, these match the View screens around them:
            // light, in Clementine's colours rather than the wallpaper's, and on their white card.
            ClementineTheme(darkTheme = false, dynamicColor = false) {
                // Test tags as resource IDs, for UI Automator.
                Surface(
                    Modifier.semantics { testTagsAsResourceId = true },
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    content()
                }
            }
        }
    }
}
