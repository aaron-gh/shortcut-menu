package io.github.aaron_gh.shortcutmenu

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.ref.WeakReference

/**
 * The menu that the shortcut opens.
 *
 * With a screen reader on, it is a plain list of buttons that the screen reader reads. With none on,
 * it speaks for itself: see [ExploreMenuView]. Choosing a service turns it on or off, and closing the
 * menu in any way turns this app's service off, ready for the next press of the shortcut.
 */
class MenuActivity : Activity() {
  private lateinit var services: Services
  private lateinit var entries: List<MenuEntry>
  private val handler = Handler(Looper.getMainLooper())
  private var tts: TextToSpeech? = null
  private var ttsReady = false
  private var pendingSpeech: String? = null
  private var onSpoken: (() -> Unit)? = null
  private var finished = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    current = WeakReference(this)
    // The application context, since switching screen readers outlives this screen.
    services = Services(applicationContext)
    entries = if (services.canWrite()) services.menuEntries() else emptyList()
    tts = TextToSpeech(this) { status -> onTtsInit(status) }

    val screenReaderOn =
      getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled
    if (screenReaderOn) {
      setContentView(buildList())
    } else {
      setContentView(buildExploreView())
      hideSystemBars()
      speak(introText(), flush = false)
    }
  }

  override fun onStop() {
    super.onStop()
    // Leaving the menu, such as for the home screen, or turning the screen off, closes it.
    if (!isChangingConfigurations) {
      close()
    }
  }

  override fun onDestroy() {
    if (current?.get() == this) {
      current = null
    }
    handler.removeCallbacksAndMessages(null)
    tts?.shutdown()
    tts = null
    super.onDestroy()
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    close()
  }

  private fun buildList(): View {
    val padding = dp(16)
    val list =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padding, padding, padding, padding)
      }
    list.addView(
      TextView(this).apply {
        setText(R.string.menu_title)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        isAccessibilityHeading = true
      }
    )
    if (!services.canWrite()) {
      list.addView(TextView(this).apply { text = getString(R.string.menu_needs_permission) })
    } else if (entries.isEmpty()) {
      list.addView(TextView(this).apply { setText(R.string.menu_empty) })
    }
    for (entry in entries) {
      list.addView(
        Button(this).apply {
          text = entryText(entry)
          setTextSize(TypedValue.COMPLEX_UNIT_SP, BUTTON_TEXT_SP)
          setOnClickListener { choose(entry) }
        },
        rowParams(),
      )
    }
    list.addView(
      Button(this).apply {
        setText(R.string.menu_close)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, BUTTON_TEXT_SP)
        setOnClickListener { close() }
      },
      rowParams(),
    )
    // The buttons share the screen, as in the menu without a screen reader, and scroll only when
    // there are too many to fit.
    return ScrollView(this).apply {
      fitsSystemWindows = true
      isFillViewport = true
      addView(list)
    }
  }

  private fun rowParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)

  private fun buildExploreView(): View {
    val labels = entries.map { entryText(it) } + getString(R.string.menu_close)
    return ExploreMenuView(
      this,
      labels,
      onHover = { row -> speak(labels[row], flush = true) },
      onChoose = { row ->
        if (row < entries.size) {
          choose(entries[row])
        } else {
          speak(getString(R.string.menu_closed), flush = true) { close() }
        }
      },
    )
  }

  private fun introText(): String =
    when {
      !services.canWrite() -> getString(R.string.menu_intro_needs_permission)
      entries.isEmpty() -> getString(R.string.menu_intro_empty)
      else -> getString(R.string.menu_intro)
    }

  private fun entryText(entry: MenuEntry): String =
    getString(if (entry.on) R.string.entry_on else R.string.entry_off, entry.label)

  /**
   * Turns [entry] on or off. A screen reader that turns on speaks for itself. Otherwise, when no
   * screen reader will be left on, the menu says what changed before it lets go, since its own
   * service keeps the accessibility volume.
   */
  private fun choose(entry: MenuEntry) {
    if (finished) {
      return
    }
    val after = services.afterToggle(entry)
    val turningOn = !entry.on
    if (turningOn && entry.screenReader) {
      apply(entry)
      return
    }
    if (services.hasScreenReader(after)) {
      apply(entry)
      return
    }
    val message = getString(if (turningOn) R.string.turned_on else R.string.turned_off, entry.label)
    speak(message, flush = true) { apply(entry) }
  }

  private fun apply(entry: MenuEntry) {
    if (finished) {
      return
    }
    finished = true
    val services = services
    services.toggle(entry) { allowed ->
      if (!allowed) {
        services.turnOffSelf()
      }
    }
    finish()
  }

  /** Closes the menu without changing anything. */
  private fun close() {
    if (finished) {
      return
    }
    finished = true
    services.turnOffSelf()
    finish()
  }

  /** Speaks [text], and then runs [done], or runs it anyway if speech does not finish in time. */
  private fun speak(text: String, flush: Boolean, done: (() -> Unit)? = null) {
    onSpoken = done
    if (done != null) {
      handler.removeCallbacksAndMessages(SPEECH_TIMEOUT)
      handler.postDelayed({ runOnSpoken() }, SPEECH_TIMEOUT, SPEECH_TIMEOUT_MS)
    }
    val engine = tts
    if (engine == null || !ttsReady) {
      pendingSpeech = if (flush || pendingSpeech == null) text else "$pendingSpeech. $text"
      return
    }
    engine.speak(
      text,
      if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
      null,
      if (done != null) UTTERANCE_DONE else UTTERANCE_PLAIN,
    )
  }

  private fun runOnSpoken() {
    val done = onSpoken ?: return
    onSpoken = null
    handler.removeCallbacksAndMessages(SPEECH_TIMEOUT)
    done()
  }

  private fun onTtsInit(status: Int) {
    val engine = tts ?: return
    if (status != TextToSpeech.SUCCESS) {
      return
    }
    engine.setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    )
    engine.setOnUtteranceProgressListener(
      object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
          if (utteranceId == UTTERANCE_DONE) {
            handler.post { runOnSpoken() }
          }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
          onDone(utteranceId)
        }
      }
    )
    ttsReady = true
    pendingSpeech?.let { text ->
      pendingSpeech = null
      engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, if (onSpoken != null) UTTERANCE_DONE else UTTERANCE_PLAIN)
    }
  }

  private fun hideSystemBars() {
    window.insetsController?.let {
      it.hide(WindowInsets.Type.systemBars())
      it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
  }

  private fun dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics)
      .toInt()

  companion object {
    private const val UTTERANCE_PLAIN = "plain"
    private const val UTTERANCE_DONE = "done"
    private val SPEECH_TIMEOUT = Any()
    private const val SPEECH_TIMEOUT_MS = 4000L

    /** The size of the buttons' text, the same as in the menu without a screen reader. */
    private const val BUTTON_TEXT_SP = 28f

    private var current: WeakReference<MenuActivity>? = null

    fun open(context: Context) {
      context.startActivity(
        Intent(context, MenuActivity::class.java)
          .addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
              Intent.FLAG_ACTIVITY_CLEAR_TASK or
              Intent.FLAG_ACTIVITY_NO_ANIMATION
          )
      )
    }

    /** Closes the open menu, if any, without changing anything. */
    fun closeOpenMenu() {
      current?.get()?.close()
    }
  }
}
