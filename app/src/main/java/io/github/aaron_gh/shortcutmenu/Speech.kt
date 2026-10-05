package io.github.aaron_gh.shortcutmenu

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** How the menu speaks. */
interface Speaker {
  /**
   * Speaks [text], after what is being spoken, or in place of it when [flush] is set. Then calls
   * [done] on the main thread, if given, unless the speech was cut off.
   */
  fun speak(text: String, flush: Boolean, done: (() -> Unit)?)

  fun shutdown()
}

/** The accessibility volume, which the user keeps up for their screen reader. */
private val SPEECH_ATTRIBUTES: AudioAttributes =
  AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/** Speaks with the phone's text-to-speech engine. */
class SystemSpeaker(context: Context) : Speaker {
  private val handler = Handler(Looper.getMainLooper())
  private var tts: TextToSpeech? = TextToSpeech(context) { status -> onInit(status) }
  private var ready = false
  private var pending: String? = null
  private var done: (() -> Unit)? = null

  override fun speak(text: String, flush: Boolean, done: (() -> Unit)?) {
    this.done = done
    val engine = tts
    if (engine == null || !ready) {
      pending = if (flush || pending == null) text else "$pending. $text"
      return
    }
    engine.speak(
      text,
      if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
      null,
      if (done != null) UTTERANCE_DONE else UTTERANCE_PLAIN,
    )
  }

  override fun shutdown() {
    done = null
    tts?.shutdown()
    tts = null
  }

  private fun onInit(status: Int) {
    val engine = tts ?: return
    if (status != TextToSpeech.SUCCESS) {
      return
    }
    engine.setAudioAttributes(SPEECH_ATTRIBUTES)
    engine.setOnUtteranceProgressListener(
      object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
          if (utteranceId == UTTERANCE_DONE) {
            handler.post { done?.invoke() }
          }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
          onDone(utteranceId)
        }
      }
    )
    ready = true
    pending?.let { text ->
      pending = null
      engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, if (done != null) UTTERANCE_DONE else UTTERANCE_PLAIN)
    }
  }

  private companion object {
    const val UTTERANCE_PLAIN = "plain"
    const val UTTERANCE_DONE = "done"
  }
}

/**
 * Speaks with Pico, bundled with the app, for safe mode, when the phone's own engine may be what
 * broke. Pico makes each phrase on a thread of its own, faster than it plays, and each phrase plays
 * from a track of its own, so that cutting one off is only a matter of stopping its track.
 */
class PicoSpeaker(context: Context) : Speaker {
  private val main = Handler(Looper.getMainLooper())
  private val thread = HandlerThread("pico").apply { start() }
  private val worker = Handler(thread.looper)
  /** Goes up with each flush, so the worker drops what was queued before it. */
  private val generation = AtomicInteger()
  private var pico: PicoTts? = null

  init {
    val appContext = context.applicationContext
    worker.post { pico = PicoTts.open(appContext, PicoVoice.forLocale(Locale.getDefault())) }
  }

  override fun speak(text: String, flush: Boolean, done: (() -> Unit)?) {
    val current = if (flush) generation.incrementAndGet() else generation.get()
    worker.post { play(text, current, done) }
  }

  override fun shutdown() {
    generation.incrementAndGet()
    worker.post {
      pico?.close()
      pico = null
      thread.quitSafely()
    }
  }

  /** Runs on the worker. Plays [text] to the end, unless a flush cuts it off. */
  private fun play(text: String, current: Int, done: (() -> Unit)?) {
    if (generation.get() != current) {
      return
    }
    val samples = pico?.synthesize(text)
    if (samples == null || samples.isEmpty()) {
      // Pico failed: there is nothing to wait for.
      done?.let { main.post(it) }
      return
    }
    val track =
      try {
        AudioTrack.Builder()
          .setAudioAttributes(SPEECH_ATTRIBUTES)
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(PicoTts.SAMPLE_RATE)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setTransferMode(AudioTrack.MODE_STATIC)
          .setBufferSizeInBytes(samples.size * 2)
          .build()
      } catch (e: Exception) {
        done?.let { main.post(it) }
        return
      }
    try {
      track.write(samples, 0, samples.size)
      track.play()
      val deadline = SystemClock.uptimeMillis() + samples.size * 1000L / PicoTts.SAMPLE_RATE + PLAY_SLACK_MS
      while (
        generation.get() == current &&
          track.playbackHeadPosition < samples.size &&
          SystemClock.uptimeMillis() < deadline
      ) {
        SystemClock.sleep(POLL_MS)
      }
      if (generation.get() == current) {
        done?.let { main.post(it) }
      }
    } catch (e: IllegalStateException) {
      done?.let { main.post(it) }
    } finally {
      track.release()
    }
  }

  private companion object {
    const val POLL_MS = 10L
    const val PLAY_SLACK_MS = 1000L
  }
}
