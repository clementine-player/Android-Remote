package de.qspool.clementineremote.ui.downloads

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.qspool.clementineremote.backend.sync.RefreshPolicy
import de.qspool.clementineremote.ui.theme.ClementineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Keep on this phone sheet asks how to keep something up to date, then keeps it. */
@RunWith(RobolectricTestRunner::class)
class KeepSheetTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun keepsAsChosen() {
        val kept = mutableListOf<RefreshPolicy>()
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                KeepSheetContent("Kind of Blue", RefreshPolicy.NEVER, onKeep = { kept += it })
            }
        }

        compose.onNodeWithText("Kind of Blue").assertIsDisplayed()
        compose.onNodeWithText("Update it").assertIsDisplayed()
        compose.onNodeWithText("Keep the songs it has now").assertIsDisplayed()
        compose.onNodeWithTag("keepRefreshnever").assertIsSelected()
        compose.onNodeWithTag("keepRefreshon_connect").assertIsNotSelected()

        compose.onNodeWithText("Every time I connect").performClick()
        compose.onNodeWithTag("keepRefreshon_connect").assertIsSelected()
        compose.onNodeWithTag("keepConfirm").performClick()

        assertEquals(listOf(RefreshPolicy.ON_CONNECT), kept)
    }
}
