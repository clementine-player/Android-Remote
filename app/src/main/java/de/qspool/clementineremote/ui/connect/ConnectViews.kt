package de.qspool.clementineremote.ui.connect

import androidx.compose.ui.platform.ComposeView
import de.qspool.clementineremote.ui.ComposeViews

/** The connect screen, in Compose, for the activity that hosts it. */
object ConnectViews {

    /** Shows the connect screen in [view], for [viewModel]'s state, telling [actions] what the user does. */
    @JvmStatic
    fun showConnect(view: ComposeView, viewModel: ConnectViewModel, actions: ConnectActions) =
        ComposeViews.show(view, followsSystemTheme = true) {
            ConnectScreen(viewModel, actions)
        }
}
