package nl.codeinfinity.coreassistant

import android.content.ComponentName
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowPackageManager

@RunWith(AndroidJUnit4::class)
class ScreenUiTest {

    init {
        val pm = RuntimeEnvironment.getApplication().packageManager
        val shadowPm = Shadows.shadowOf(pm)
        shadowPm.addActivityIfNotPresent(
            ComponentName("nl.codeinfinity.coreassistant", "androidx.activity.ComponentActivity")
        )
    }

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun licensesScreen_displaysTitle() {
        composeTestRule.setContent {
            LicensesScreen(onBack = {})
        }
        composeTestRule.onNodeWithText("Open Source Licenses").assertExists()
    }

    @Test
    fun licensesScreen_displaysCoreApplicationSection() {
        composeTestRule.setContent {
            LicensesScreen(onBack = {})
        }
        composeTestRule.onNodeWithText("Core Application").assertExists()
    }

    @Test
    fun licensesScreen_displaysNetworkingSection() {
        composeTestRule.setContent {
            LicensesScreen(onBack = {})
        }
        composeTestRule.onNodeWithText("Networking & AI").assertExists()
    }

    @Test
    fun licensesScreen_backButtonExists() {
        composeTestRule.setContent {
            LicensesScreen(onBack = {})
        }
        composeTestRule.onNodeWithContentDescription("Back").assertExists()
    }

    @Test
    fun simpleComposable_rendersText() {
        composeTestRule.setContent {
            Text("Hello, World!")
        }
        composeTestRule.onNodeWithText("Hello, World!").assertExists()
    }
}
