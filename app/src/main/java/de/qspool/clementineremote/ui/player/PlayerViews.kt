package de.qspool.clementineremote.ui.player

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import de.qspool.clementineremote.ui.theme.ClementineTheme

/**
 * The player's Compose parts, shown in the View fragments that host them until the whole player
 * moves to Compose.
 */
object PlayerViews {

    /** Shows the player page in [view]. Tapping the artwork runs [onArtClick]. */
    @JvmStatic
    fun showNowPlaying(view: ComposeView, onArtClick: Runnable) = show(view) {
        NowPlaying(onArtClick = onArtClick::run)
    }

    /** Shows the player's controls in [view]. */
    @JvmStatic
    fun showControls(view: ComposeView) = show(view) {
        PlayerControls()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun show(view: ComposeView, content: @Composable () -> Unit) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            // The View screens around these are light only, so these are too until the app's
            // shell moves to Compose.
            ClementineTheme(darkTheme = false) {
                // Test tags as resource IDs, for UI Automator.
                Surface(Modifier.semantics { testTagsAsResourceId = true }) {
                    content()
                }
            }
        }
    }
}
