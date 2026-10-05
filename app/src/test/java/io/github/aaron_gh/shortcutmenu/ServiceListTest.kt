package io.github.aaron_gh.shortcutmenu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceListTest {
  private val self = "io.github.aaron_gh.shortcutmenu/io.github.aaron_gh.shortcutmenu.ShortcutMenuService"
  private val backtalk = "fyi.quin.backtalk/com.google.android.marvin.talkback.TalkBackService"
  private val talkback =
    "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
  private val selectToSpeak =
    "com.google.android.marvin.talkback/com.google.android.accessibility.selecttospeak.SelectToSpeakService"
  private val readers = setOf(backtalk, talkback)

  @Test
  fun parsesAndJoins() {
    assertEquals(listOf("a/b", "c/d"), ServiceList.parse("a/b:c/d"))
    assertEquals(emptyList<String>(), ServiceList.parse(null))
    assertEquals(emptyList<String>(), ServiceList.parse(""))
    assertEquals("a/b:c/d", ServiceList.join(listOf("a/b", "c/d")))
  }

  @Test
  fun shortClassNamesMatchLongOnes() {
    assertTrue(ServiceList.contains(listOf("com.example/.Service"), "com.example/com.example.Service"))
    assertFalse(ServiceList.contains(listOf("com.example/.Other"), "com.example/com.example.Service"))
  }

  @Test
  fun turningOnAddsTheServiceAndRemovesTheMenu() {
    assertEquals(listOf(backtalk), ServiceList.toggle(listOf(self), backtalk, self, readers))
  }

  @Test
  fun turningOffRemovesTheServiceAndTheMenu() {
    assertEquals(
      listOf(selectToSpeak),
      ServiceList.toggle(listOf(backtalk, self, selectToSpeak), backtalk, self, readers),
    )
  }

  @Test
  fun turningOnAScreenReaderTurnsTheOtherOff() {
    assertEquals(
      listOf(selectToSpeak, talkback),
      ServiceList.toggle(listOf(backtalk, selectToSpeak, self), talkback, self, readers),
    )
  }

  @Test
  fun turningOnAnotherServiceKeepsTheScreenReader() {
    assertEquals(
      listOf(backtalk, selectToSpeak),
      ServiceList.toggle(listOf(backtalk, self), selectToSpeak, self, readers),
    )
  }

  @Test
  fun shortFormInTheSettingIsStillToggledOff() {
    assertEquals(
      emptyList<String>(),
      ServiceList.toggle(listOf("fyi.quin.backtalk/com.google.android.marvin.talkback.TalkBackService", "io.github.aaron_gh.shortcutmenu/.ShortcutMenuService"), backtalk, self, readers),
    )
  }

  @Test
  fun switchingScreenReadersStopsTheOldOneFirst() {
    val enabled = listOf(backtalk, selectToSpeak, self)
    val after = ServiceList.toggle(enabled, talkback, self, readers)
    assertEquals(listOf(backtalk), ServiceList.mustStopFirst(enabled, after, readers))
  }

  @Test
  fun startingAScreenReaderWithNoneOnNeedsNoWait() {
    val enabled = listOf(selectToSpeak, self)
    val after = ServiceList.toggle(enabled, backtalk, self, readers)
    assertEquals(emptyList<String>(), ServiceList.mustStopFirst(enabled, after, readers))
  }

  @Test
  fun turningAScreenReaderOffNeedsNoWait() {
    val enabled = listOf(backtalk, self)
    val after = ServiceList.toggle(enabled, backtalk, self, readers)
    assertEquals(emptyList<String>(), ServiceList.mustStopFirst(enabled, after, readers))
  }

  @Test
  fun safeModeTurnsEveryScreenReaderOffAndKeepsTheRest() {
    assertEquals(
      listOf(self, selectToSpeak),
      ServiceList.withoutScreenReaders(listOf(backtalk, self, selectToSpeak, talkback), readers),
    )
  }

  @Test
  fun safeModeMatchesShortClassNames() {
    assertEquals(
      emptyList<String>(),
      ServiceList.withoutScreenReaders(listOf("com.example/.Reader"), setOf("com.example/com.example.Reader")),
    )
  }

  @Test
  fun endingSafeModeTurnsTheScreenReadersBackOnAndTheMenuOff() {
    assertEquals(
      listOf(selectToSpeak, backtalk),
      ServiceList.withScreenReadersBack(listOf(self, selectToSpeak), listOf(backtalk), self),
    )
    assertEquals(
      listOf(backtalk),
      ServiceList.withScreenReadersBack(listOf(backtalk), listOf(backtalk), self),
    )
  }
}
