package io.github.aaron_gh.shortcutmenu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickPressesTest {
  @Test
  fun threeQuickPressesCount() {
    val presses = QuickPresses(3, 600)
    assertFalse(presses.press(1000))
    assertFalse(presses.press(1400))
    assertTrue(presses.press(1900))
  }

  @Test
  fun aSlowPressStartsAgain() {
    val presses = QuickPresses(3, 600)
    assertFalse(presses.press(1000))
    assertFalse(presses.press(1400))
    assertFalse(presses.press(2100))
    assertFalse(presses.press(2500))
    assertTrue(presses.press(3000))
  }

  @Test
  fun countingStartsAgainAfterwards() {
    val presses = QuickPresses(3, 600)
    presses.press(0)
    presses.press(100)
    assertTrue(presses.press(200))
    assertFalse(presses.press(300))
    assertFalse(presses.press(400))
    assertTrue(presses.press(500))
  }

  @Test
  fun resetStartsAgain() {
    val presses = QuickPresses(3, 600)
    presses.press(0)
    presses.press(100)
    presses.reset()
    assertFalse(presses.press(200))
  }
}
