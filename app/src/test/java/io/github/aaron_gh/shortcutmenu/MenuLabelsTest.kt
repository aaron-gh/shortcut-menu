package io.github.aaron_gh.shortcutmenu

import io.github.aaron_gh.shortcutmenu.MenuLabels.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuLabelsTest {
  @Test
  fun uniqueNamesStayAsTheyAre() {
    assertEquals(
      listOf("Backtalk", "Select to Speak"),
      MenuLabels.distinct(
        listOf(
          Service("Backtalk", "fyi.quin.backtalk"),
          Service("Select to Speak", "com.google.android.marvin.talkback"),
        )
      ),
    )
  }

  @Test
  fun sharedNamesGetTheMaker() {
    assertEquals(
      listOf("TalkBack (Samsung)", "TalkBack (Google)", "Backtalk"),
      MenuLabels.distinct(
        listOf(
          Service("TalkBack", "com.samsung.android.accessibility.talkback"),
          Service("TalkBack", "com.google.android.marvin.talkback"),
          Service("Backtalk", "fyi.quin.backtalk"),
        )
      ),
    )
  }

  @Test
  fun sharedNamesFromTheSameMakerGetThePackage() {
    assertEquals(
      listOf("TalkBack (com.example.one)", "TalkBack (com.example.two)"),
      MenuLabels.distinct(
        listOf(Service("TalkBack", "com.example.one"), Service("TalkBack", "com.example.two"))
      ),
    )
  }

  @Test
  fun makerIsTheSecondPartOfThePackage() {
    assertEquals("Samsung", MenuLabels.maker("com.samsung.android.accessibility.talkback"))
    assertEquals("Appsuite", MenuLabels.maker("eu.appsuite.talkforward"))
    assertEquals("Single", MenuLabels.maker("single"))
  }

  @Test
  fun picoCanSayLatinNamesAndNumbers() {
    assertTrue(MenuLabels.speakable("TalkBack"))
    assertTrue(MenuLabels.speakable("Lecteur d'écran"))
    assertTrue(MenuLabels.speakable("Ekran okuyucu"))
    assertTrue(MenuLabels.speakable("解说 2"))
    assertTrue(MenuLabels.speakable("解说 TalkBack"))
  }

  @Test
  fun picoCannotSayOtherAlphabets() {
    assertFalse(MenuLabels.speakable("Программа чтения с экрана"))
    assertFalse(MenuLabels.speakable("解说"))
    assertFalse(MenuLabels.speakable("قارئ الشاشة"))
    assertFalse(MenuLabels.speakable("स्क्रीन रीडर"))
    assertFalse(MenuLabels.speakable("Ελληνικά"))
    assertFalse(MenuLabels.speakable(""))
    assertFalse(MenuLabels.speakable(" (), "))
  }
}
