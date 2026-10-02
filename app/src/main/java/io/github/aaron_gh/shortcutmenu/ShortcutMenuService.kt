package io.github.aaron_gh.shortcutmenu

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * The service that the accessibility shortcut turns on and off.
 *
 * The shortcut turns it on, and it opens the menu. When the menu closes, the service turns itself
 * off again, so that the next press of the shortcut turns it on and opens the menu once more. A
 * press while the menu is open turns the service off, which closes the menu.
 */
class ShortcutMenuService : AccessibilityService() {
  override fun onServiceConnected() {
    instance = this
    MenuActivity.open(this)
  }

  override fun onUnbind(intent: Intent?): Boolean {
    instance = null
    MenuActivity.closeOpenMenu()
    return super.onUnbind(intent)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

  override fun onInterrupt() {}

  companion object {
    /** The running service, while it is on. */
    @Volatile
    var instance: ShortcutMenuService? = null
      private set
  }
}
