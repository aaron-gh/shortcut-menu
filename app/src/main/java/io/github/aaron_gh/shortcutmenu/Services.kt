package io.github.aaron_gh.shortcutmenu

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
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

  /** Every installed accessibility service but this one, sorted by name. */
  fun installed(): List<MenuEntry> {
    val enabled = enabled()
    return accessibilityManager.installedAccessibilityServiceList
      .map { info ->
        val component = componentOf(info)
        MenuEntry(
          component = component,
          label = info.resolveInfo.loadLabel(context.packageManager).toString(),
          on = ServiceList.contains(enabled, component),
          screenReader = isScreenReader(info),
        )
      }
      .filterNot { ServiceList.normalize(it.component) == ServiceList.normalize(self) }
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

  /** Turns [entry] on or off, and this app's service off. Returns false if it is not allowed. */
  fun toggle(entry: MenuEntry): Boolean = write(afterToggle(entry))

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

    fun componentOf(info: AccessibilityServiceInfo): String =
      ComponentName(info.resolveInfo.serviceInfo.packageName, info.resolveInfo.serviceInfo.name)
        .flattenToString()

    /** A screen reader takes over touch, so two of them cannot run at once. */
    fun isScreenReader(info: AccessibilityServiceInfo): Boolean =
      info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE != 0
  }
}
