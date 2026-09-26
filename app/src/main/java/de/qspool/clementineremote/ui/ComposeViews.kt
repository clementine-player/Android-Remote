package de.qspool.clementineremote.ui

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

/** Compose screens shown in the View-based activities and fragments, until the app's shell moves to Compose. */
object ComposeViews {

    /** Shows [content] in [view], in the app's theme, disposed of with the view's lifecycle. */
    @OptIn(ExperimentalComposeUiApi::class)
    fun show(view: ComposeView, content: @Composable () -> Unit) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            // Until the app's shell moves to Compose, these match the View screens around them:
            // light, in Clementine's colours rather than the wallpaper's, and drawn straight onto
            // the background those screens give them.
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
