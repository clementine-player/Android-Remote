package de.qspool.clementineremote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Kotlin, the Compose compiler and Compose's test rule all work in the unit tests. */
@RunWith(RobolectricTestRunner::class)
class ComposeSetupTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersMaterial3() {
        compose.setContent {
            MaterialTheme {
                Text("Clementine")
            }
        }

        compose.onNodeWithText("Clementine").assertIsDisplayed()
    }
}
