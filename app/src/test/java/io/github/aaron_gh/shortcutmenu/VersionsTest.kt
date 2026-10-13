package io.github.aaron_gh.shortcutmenu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
  @Test
  fun laterVersionsAreNewer() {
    assertTrue(Versions.isNewer("0.6", "0.5.1"))
    assertTrue(Versions.isNewer("0.5.2", "0.5.1"))
    assertTrue(Versions.isNewer("0.10", "0.9"))
    assertTrue(Versions.isNewer("1.0", "0.9.9"))
  }

  @Test
  fun sameOrEarlierVersionsAreNot() {
    assertFalse(Versions.isNewer("0.5.1", "0.5.1"))
    assertFalse(Versions.isNewer("0.5", "0.5.0"))
    assertFalse(Versions.isNewer("0.5", "0.5.1"))
    assertFalse(Versions.isNewer("0.4", "0.5"))
  }

  @Test
  fun leadingVAndSuffixesAreIgnored() {
    assertTrue(Versions.isNewer("v0.6", "0.5"))
    assertFalse(Versions.isNewer("0.5-beta", "0.5"))
  }
}
