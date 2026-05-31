package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioDspViewModel
import com.example.db.DspSettings
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.DspThemeSelector
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = AudioDspViewModel(application)
    val dspSettings = DspSettings()

    composeTestRule.setContent { 
      MyApplicationTheme(dspTheme = DspThemeSelector.CyberObsidian) { 
        BroAudioControlCenter(viewModel = viewModel, dspSettings = dspSettings) 
      } 
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
