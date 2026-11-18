package com.gamut

import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.gamut.feature.playerui.*
import com.gamut.spot.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordsInteractionTest {
    @Test fun tappingTimedTextReturnsToCoverInBothSizesAndNeverSeeks() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        var seeks = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                var words by remember { mutableStateOf(true) }
                var expanded by remember { mutableStateOf(false) }
                SpotTheme {
                    // MainActivity draws behind system bars. Keep this standalone
                    // fixture inside them, as the real player's container does.
                    Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
                        if (!words) Text("Cover fixture", Modifier.clickable { words = true }.padding(24.dp))
                        else SpotWordsPanel("Lyrics", LyricsState.Loaded(listOf(LyricsLine(0, "First timed line", 1000), LyricsLine(1000, "Second timed line", 2000))), 1200, Color.DarkGray, expanded,
                            { expanded = !expanded }, { expanded = false; words = false }, { seeks++ })
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.textContains("Second timed line")), 5000))
            device.findObject(By.textContains("Second timed line")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Cover fixture")), 5000))
            device.findObject(By.text("Cover fixture")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Expand Lyrics")), 5000))
            device.findObject(By.desc("Expand Lyrics")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Minimize text")), 5000))
            device.findObject(By.textContains("First timed line")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Cover fixture")), 5000))
            assertEquals(0, seeks)
        }
    }
}
