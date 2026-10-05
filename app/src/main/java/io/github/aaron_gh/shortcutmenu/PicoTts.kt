package io.github.aaron_gh.shortcutmenu

import android.content.Context
import java.io.File
import java.util.Locale

/** One of the voices bundled with the app, in assets/pico: its text analysis and signal files. */
data class PicoVoice(val ta: String, val sg: String) {
  companion object {
    val BRITISH = PicoVoice("en-GB_ta.bin", "en-GB_kh0_sg.bin")
    val AMERICAN = PicoVoice("en-US_ta.bin", "en-US_lh0_sg.bin")

    /** American English in the United States, and British English everywhere else. */
    fun forLocale(locale: Locale): PicoVoice = if (locale.country == "US") AMERICAN else BRITISH
  }
}

/**
 * SVOX Pico, the speech engine bundled with the app, opened with one voice. It makes 16 kHz, 16-bit
 * mono samples. Use it from one thread only.
 */
class PicoTts private constructor(private var handle: Long) {
  /** The samples for [text], or null if Pico failed. */
  fun synthesize(text: String): ShortArray? = if (handle == 0L) null else nativeSynthesize(handle, text)

  fun close() {
    if (handle != 0L) {
      nativeClose(handle)
      handle = 0L
    }
  }

  companion object {
    const val SAMPLE_RATE = 16000

    /** Opens Pico with [voice], or returns null if it cannot. */
    fun open(context: Context, voice: PicoVoice): PicoTts? {
      return try {
        System.loadLibrary("picotts")
        val ta = voiceFile(context, voice.ta)
        val sg = voiceFile(context, voice.sg)
        val handle = nativeOpen(ta.path, sg.path)
        if (handle == 0L) null else PicoTts(handle)
      } catch (e: Exception) {
        null
      } catch (e: UnsatisfiedLinkError) {
        null
      }
    }

    /**
     * Pico reads its voices from files, so they are copied out of the assets the first time. The copy
     * is renamed into place only once it is whole.
     */
    private fun voiceFile(context: Context, name: String): File {
      val dir = File(context.noBackupFilesDir, "pico").apply { mkdirs() }
      val file = File(dir, name)
      if (!file.exists()) {
        val partial = File(dir, "$name.partial")
        context.assets.open("pico/$name").use { input ->
          partial.outputStream().use { input.copyTo(it) }
        }
        partial.renameTo(file)
      }
      return file
    }

    @JvmStatic private external fun nativeOpen(taPath: String, sgPath: String): Long

    @JvmStatic private external fun nativeSynthesize(handle: Long, text: String): ShortArray?

    @JvmStatic private external fun nativeClose(handle: Long)
  }
}
