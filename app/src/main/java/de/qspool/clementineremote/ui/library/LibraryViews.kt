package de.qspool.clementineremote.ui.library

import androidx.compose.ui.platform.ComposeView
import de.qspool.clementineremote.ui.ComposeViews

/** The library, in Compose, for the View-based fragment that hosts it until the app's shell moves to Compose. */
object LibraryViews {

    /** Shows the library in [view], for [viewModel]'s state. */
    @JvmStatic
    fun showLibrary(view: ComposeView, viewModel: LibraryViewModel) = ComposeViews.show(view) {
        LibraryScreen(viewModel)
    }
}
