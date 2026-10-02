package io.github.aaron_gh.shortcutmenu

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * The service that the accessibility shortcut turns on.
 *
 * The shortcut turns it on, and it opens the menu. When the menu closes, the service turns itself
 * off again, so that the next press of the shortcut turns it on and opens the menu once more. It
 * asks for the accessibility button, so a press while it is on, with the menu open, reaches it as a
 * click instead of turning it off, and closes the menu.
 */
class ShortcutMenuService : AccessibilityService() {
  private val buttonCallback =
    object : AccessibilityButtonController.AccessibilityButtonCallback() {
      override fun onClicked(controller: AccessibilityButtonController) {
        if (MenuActivity.isOpen()) {
          MenuActivity.closeOpenMenu()
        } else {
          MenuActivity.open(this@ShortcutMenuService)
        }
      }
    }

  override fun onServiceConnected() {
    instance = this
    accessibilityButtonController.registerAccessibilityButtonCallback(buttonCallback)
    MenuActivity.open(this)
  }

  override fun onUnbind(intent: Intent?): Boolean {
    accessibilityButtonController.unregisterAccessibilityButtonCallback(buttonCallback)
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
