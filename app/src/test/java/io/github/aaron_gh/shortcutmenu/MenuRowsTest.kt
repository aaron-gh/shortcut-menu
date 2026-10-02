package io.github.aaron_gh.shortcutmenu

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuRowsTest {
  @Test
  fun rowsShareTheHeight() {
    assertEquals(0, MenuRows.rowAt(0f, 900, 3))
    assertEquals(0, MenuRows.rowAt(299f, 900, 3))
    assertEquals(1, MenuRows.rowAt(300f, 900, 3))
    assertEquals(2, MenuRows.rowAt(899f, 900, 3))
  }

  @Test
  fun aFingerPastTheEdgeStaysOnTheNearestRow() {
    assertEquals(0, MenuRows.rowAt(-50f, 900, 3))
    assertEquals(2, MenuRows.rowAt(950f, 900, 3))
  }

  @Test
  fun noRowsMeansNoRow() {
    assertEquals(-1, MenuRows.rowAt(100f, 900, 0))
    assertEquals(-1, MenuRows.rowAt(100f, 0, 3))
  }
}
