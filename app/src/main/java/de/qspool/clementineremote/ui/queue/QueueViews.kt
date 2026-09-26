package de.qspool.clementineremote.ui.queue

import androidx.compose.ui.platform.ComposeView
import de.qspool.clementineremote.ui.ComposeViews

/** The queue, in Compose, for the View-based fragment that hosts it until the app's shell moves to Compose. */
object QueueViews {

    /** Shows the queue in [view], for [viewModel]'s state. */
    @JvmStatic
    fun showQueue(view: ComposeView, viewModel: QueueViewModel) = ComposeViews.show(view) {
        QueueScreen(viewModel)
    }
}
