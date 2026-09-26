package de.qspool.clementineremote.ui.downloads

import androidx.compose.ui.platform.ComposeView
import de.qspool.clementineremote.ui.ComposeViews

/** Downloads, in Compose, for the View-based fragment that hosts it until the app's shell moves to Compose. */
object DownloadsViews {

    /** Shows the downloads in [view], for [viewModel]'s state. */
    @JvmStatic
    fun showDownloads(view: ComposeView, viewModel: DownloadsViewModel) = ComposeViews.show(view) {
        DownloadsScreen(viewModel)
    }
}
