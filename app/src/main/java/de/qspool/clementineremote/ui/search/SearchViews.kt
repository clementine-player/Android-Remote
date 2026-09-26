package de.qspool.clementineremote.ui.search

import androidx.compose.ui.platform.ComposeView
import de.qspool.clementineremote.ui.ComposeViews

/** Search, in Compose, for the View-based fragment that hosts it until the app's shell moves to Compose. */
object SearchViews {

    /** Shows search in [view], for [viewModel]'s state. */
    @JvmStatic
    fun showSearch(view: ComposeView, viewModel: SearchViewModel) = ComposeViews.show(view) {
        SearchScreen(viewModel)
    }
}
