package io.github.aaron_gh.shortcutmenu

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** An accessibility service that the menu can turn on or off. */
data class MenuEntry(
  val component: String,
  val label: String,
  val on: Boolean,
  val screenReader: Boolean,
)

/** Reads and changes which accessibility services are on, and which ones the menu offers. */
class Services(private val context: Context) {
  private val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
  private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** This app's own service, which never appears in the menu. */
  val self: String = ComponentName(context, ShortcutMenuService::class.java).flattenToString()

  /** Whether the user granted the permission to turn services on and off. */
  fun canWrite(): Boolean =
    context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
      PackageManager.PERMISSION_GRANTED

  /**
   * Every installed accessibility service but this one, sorted by name. Services with the same
   * name, such as Google's and Samsung's TalkBack, get their maker added.
   */
  fun installed(): List<MenuEntry> {
    val enabled = enabled()
    val infos =
      accessibilityManager.installedAccessibilityServiceList.filterNot {
        ServiceList.normalize(componentOf(it)) == ServiceList.normalize(self)
      }
    val labels =
      MenuLabels.distinct(
        infos.map { info ->
          MenuLabels.Service(
            info.resolveInfo.loadLabel(context.packageManager).toString(),
            info.resolveInfo.serviceInfo.packageName,
          )
        }
      )
    return infos
      .mapIndexed { index, info ->
        val component = componentOf(info)
        MenuEntry(
          component = component,
          label = labels[index],
          on = ServiceList.contains(enabled, component),
          screenReader = isScreenReader(info),
        )
      }
      .sortedBy { it.label.lowercase() }
  }

  /** The services the user chose for the menu. Until they choose, the screen readers. */
  fun chosen(): Set<String> =
    prefs.getStringSet(KEY_CHOSEN, null)
      ?: installed().filter { it.screenReader }.map { it.component }.toSet()

  fun setChosen(components: Set<String>) {
    prefs.edit().putStringSet(KEY_CHOSEN, components).apply()
  }

  /** The services to show in the menu. */
  fun menuEntries(): List<MenuEntry> {
    val chosen = chosen().map { ServiceList.normalize(it) }.toSet()
    return installed().filter { ServiceList.normalize(it.component) in chosen }
  }

  fun enabled(): List<String> =
    ServiceList.parse(
      Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    )

  /** What the enabled services will be after [entry] is toggled. */
  fun afterToggle(entry: MenuEntry): List<String> =
    ServiceList.toggle(enabled(), entry.component, self, screenReaders())

  /** Whether any screen reader is in [components]. */
  fun hasScreenReader(components: List<String>): Boolean {
    val readers = screenReaders().map { ServiceList.normalize(it) }.toSet()
    return components.any { ServiceList.normalize(it) in readers }
  }

  /**
   * Turns [entry] on or off, and this app's service off, then calls [done] with whether it was
   * allowed. When a screen reader turns on in place of another, the old one is turned off first,
   * and the new one only once the old one has stopped and let go of explore by touch. This app's
   * service stays on in between, so that the app keeps running.
   */
  fun toggle(entry: MenuEntry, done: (Boolean) -> Unit) {
    val enabled = enabled()
    val after = afterToggle(entry)
    val stopFirst = ServiceList.mustStopFirst(enabled, after, screenReaders())
    if (stopFirst.isEmpty()) {
      done(write(after))
      return
    }
    val stopping = stopFirst.map { ServiceList.normalize(it) }.toSet()
    if (!write(enabled.filterNot { ServiceList.normalize(it) in stopping })) {
      done(false)
      return
    }
    waitUntilStopped(stopping) { done(write(afterToggle(entry))) }
  }

  /**
   * Runs [then] once none of [components] is running and explore by touch is off, after a moment
   * for the screen reader to finish letting go, or after [STOP_TIMEOUT_MS] in any case.
   */
  private fun waitUntilStopped(components: Set<String>, then: () -> Unit) {
    val handler = Handler(Looper.getMainLooper())
    val deadline = SystemClock.uptimeMillis() + STOP_TIMEOUT_MS
    val check =
      object : Runnable {
        override fun run() {
          val running =
            accessibilityManager
              .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
              .map { ServiceList.normalize(componentOf(it)) }
          val stopped =
            components.none { it in running } && !accessibilityManager.isTouchExplorationEnabled
          if (stopped || SystemClock.uptimeMillis() >= deadline) {
            handler.postDelayed(then, SETTLE_MS)
          } else {
            handler.postDelayed(this, POLL_MS)
          }
        }
      }
    handler.post(check)
  }

  /** Turns this app's service off. */
  fun turnOffSelf() {
    val service = ShortcutMenuService.instance
    if (service != null) {
      service.disableSelf()
    } else if (canWrite() && ServiceList.contains(enabled(), self)) {
      write(enabled().filterNot { ServiceList.normalize(it) == ServiceList.normalize(self) })
    }
  }

  /**
   * The features assigned to a shortcut, from the secure setting [key], such as
   * "accessibility_shortcut_target_service" for the volume keys.
   */
  fun shortcutTargets(key: String): List<String> =
    ServiceList.parse(Settings.Secure.getString(context.contentResolver, key))

  /**
   * Marks Android's question the first time the volume key shortcut is used, "Use accessibility
   * shortcut?", as answered, since nobody installs Shortcut Menu but to use the shortcut. Without a
   * screen reader on, that question is a dialog that a blind user may not hear. Returns whether it
   * is now marked.
   */
  fun skipShortcutQuestion(): Boolean {
    val resolver = context.contentResolver
    if (Settings.Secure.getInt(resolver, KEY_SHORTCUT_DIALOG_SHOWN, 0) == 1) {
      return true
    }
    if (!canWrite()) {
      return false
    }
    return try {
      Settings.Secure.putInt(resolver, KEY_SHORTCUT_DIALOG_SHOWN, 1)
    } catch (e: SecurityException) {
      false
    }
  }

  /** The name of [component] if it is an installed service, or the component itself. */
  fun labelOf(component: String): String =
    installed().firstOrNull { ServiceList.normalize(it.component) == ServiceList.normalize(component) }
      ?.label ?: component

  private fun write(components: List<String>): Boolean {
    if (!canWrite()) {
      return false
    }
    val resolver = context.contentResolver
    return try {
      Settings.Secure.putString(
        resolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ServiceList.join(components),
      )
      Settings.Secure.putInt(
        resolver,
        Settings.Secure.ACCESSIBILITY_ENABLED,
        if (components.isEmpty()) 0 else 1,
      )
      true
    } catch (e: SecurityException) {
      false
    }
  }

  private fun screenReaders(): Set<String> =
    accessibilityManager.installedAccessibilityServiceList
      .filter { isScreenReader(it) }
      .map { componentOf(it) }
      .toSet()

  private companion object {
    const val PREFS = "shortcut_menu"
    const val KEY_CHOSEN = "chosen_services"
    const val POLL_MS = 50L
    const val SETTLE_MS = 250L
    const val STOP_TIMEOUT_MS = 3000L
    // Settings.Secure.ACCESSIBILITY_SHORTCUT_DIALOG_SHOWN, which the SDK hides.
    const val KEY_SHORTCUT_DIALOG_SHOWN = "accessibility_shortcut_dialog_shown"

    fun componentOf(info: AccessibilityServiceInfo): String =
      ComponentName(info.resolveInfo.serviceInfo.packageName, info.resolveInfo.serviceInfo.name)
        .flattenToString()

    /**
     * A screen reader takes over touch, so two of them cannot run at once. TalkBack and its forks
     * turn touch exploration on only while they run, so the capability to ask for it is what marks
     * one that is off.
     */
    fun isScreenReader(info: AccessibilityServiceInfo): Boolean =
      info.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION != 0 ||
        info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE != 0
  }
}
