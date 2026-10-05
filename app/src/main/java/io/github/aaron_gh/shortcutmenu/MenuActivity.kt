package io.github.aaron_gh.shortcutmenu

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.KeyEvent
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
 *
 * Pressing volume up three times quickly while it is open starts safe mode, for when a screen reader or the phone's
 * speech has broken: it turns every screen reader off, and the menu speaks for itself with Pico,
 * the speech engine bundled with the app, so that a screen reader can be turned back on.
 */
class MenuActivity : Activity() {
  private lateinit var services: Services
  private lateinit var entries: List<MenuEntry>
  private val handler = Handler(Looper.getMainLooper())
  private lateinit var speaker: Speaker
  private var onSpoken: (() -> Unit)? = null
  private var finished = false
  private var safeMode = false
  /** Whether volume up went down while the menu was open, and not as part of the shortcut. */
  private var volumeUpPressed = false
  private var volumeUpDownTime = 0L
  private var volumeDownHeld = false
  private val safeModePresses = QuickPresses(SAFE_MODE_PRESSES, SAFE_MODE_PRESS_GAP_MS)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    current = WeakReference(this)
    // The application context, since switching screen readers outlives this screen.
    services = Services(applicationContext)
    entries = if (services.canWrite()) services.menuEntries() else emptyList()
    speaker = SystemSpeaker(this)

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
    speaker.shutdown()
    super.onDestroy()
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    when (event.keyCode) {
      KeyEvent.KEYCODE_VOLUME_DOWN -> {
        volumeDownHeld = event.action == KeyEvent.ACTION_DOWN
        if (volumeDownHeld) {
          volumeUpPressed = false
          safeModePresses.reset()
        }
      }
      KeyEvent.KEYCODE_VOLUME_UP -> {
        // Only fresh presses count: not the release of the keys held down for the shortcut. Each
        // press still changes the volume as usual, so that one meant for the volume does just that.
        if (!safeMode) {
          if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            volumeUpPressed = !volumeDownHeld
            volumeUpDownTime = event.eventTime
          } else if (event.action == KeyEvent.ACTION_UP && volumeUpPressed) {
            volumeUpPressed = false
            if (safeModePresses.press(volumeUpDownTime)) {
              enterSafeMode()
            }
          }
        }
      }
    }
    return super.dispatchKeyEvent(event)
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

  /**
   * Turns every screen reader off, and speaks the menu with Pico instead of the phone's speech
   * engine. With no screen reader on, only the speech changes.
   */
  private fun enterSafeMode() {
    if (finished || safeMode) {
      return
    }
    safeMode = true
    handler.removeCallbacksAndMessages(SPEECH_TIMEOUT)
    onSpoken = null
    speaker.shutdown()
    speaker = PicoSpeaker(this)
    speak(getString(R.string.safe_mode), flush = true)
    val readerWasOn = services.hasScreenReader(services.enabled())
    services.turnOffScreenReaders {
      if (finished) {
        return@turnOffScreenReaders
      }
      entries = if (services.canWrite()) services.safeModeEntries() else emptyList()
      setContentView(buildExploreView())
      hideSystemBars()
      val intro =
        when {
          !services.canWrite() || entries.isEmpty() -> introText()
          readerWasOn -> getString(R.string.safe_mode_readers_off)
          else -> getString(R.string.safe_mode_intro)
        }
      speak(intro, flush = false)
    }
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
    speaker.speak(text, flush, if (done != null) ({ runOnSpoken() }) else null)
  }

  private fun runOnSpoken() {
    val done = onSpoken ?: return
    onSpoken = null
    handler.removeCallbacksAndMessages(SPEECH_TIMEOUT)
    done()
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
    private val SPEECH_TIMEOUT = Any()
    private const val SPEECH_TIMEOUT_MS = 4000L
    /** Safe mode takes this many presses of volume up, each this soon after the one before. */
    private const val SAFE_MODE_PRESSES = 3
    private const val SAFE_MODE_PRESS_GAP_MS = 600L

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

/** Counts presses of a key that come quickly one after another. */
class QuickPresses(private val count: Int, private val maxGapMs: Long) {
  private var presses = 0
  private var lastTime = 0L

  /**
   * Records a press at [time], in milliseconds. Returns true when it makes [count] presses in a row,
   * each no more than [maxGapMs] after the one before, and starts counting again.
   */
  fun press(time: Long): Boolean {
    presses = if (presses > 0 && time - lastTime <= maxGapMs) presses + 1 else 1
    lastTime = time
    if (presses >= count) {
      presses = 0
      return true
    }
    return false
  }

  fun reset() {
    presses = 0
  }
}
