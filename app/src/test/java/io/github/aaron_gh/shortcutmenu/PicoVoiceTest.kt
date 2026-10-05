package io.github.aaron_gh.shortcutmenu

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PicoVoiceTest {
  @Test
  fun theUnitedStatesGetsAmericanEnglish() {
    assertEquals(PicoVoice.AMERICAN, PicoVoice.forLocale(Locale.US))
    assertEquals(PicoVoice.AMERICAN, PicoVoice.forLocale(Locale.forLanguageTag("es-US")))
  }

  @Test
  fun everywhereElseGetsBritishEnglish() {
    assertEquals(PicoVoice.BRITISH, PicoVoice.forLocale(Locale.UK))
    assertEquals(PicoVoice.BRITISH, PicoVoice.forLocale(Locale.forLanguageTag("en-AU")))
    assertEquals(PicoVoice.BRITISH, PicoVoice.forLocale(Locale.GERMANY))
    assertEquals(PicoVoice.BRITISH, PicoVoice.forLocale(Locale.ENGLISH))
  }
}
