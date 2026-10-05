package io.github.aaron_gh.shortcutmenu

/**
 * Android's list of enabled accessibility services, as stored in the secure setting: flattened
 * component names, such as "com.example/com.example.Service", separated by colons. A class name may
 * also be written short, as "com.example/.Service".
 */
object ServiceList {
  fun parse(value: String?): List<String> =
    value.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() }

  fun join(components: List<String>): String = components.joinToString(":")

  /** Writes [component] with its full class name, so that the short and long forms compare equal. */
  fun normalize(component: String): String {
    val slash = component.indexOf('/')
    if (slash < 0) {
      return component
    }
    val pkg = component.substring(0, slash)
    val cls = component.substring(slash + 1)
    return if (cls.startsWith(".")) "$pkg/$pkg$cls" else component
  }

  fun contains(components: List<String>, component: String): Boolean {
    val wanted = normalize(component)
    return components.any { normalize(it) == wanted }
  }

  /**
   * Returns the enabled services after the menu toggles [target]. The menu's own service, [self],
   * always leaves the list, since the menu is done. Turning a screen reader on turns the other
   * [screenReaders] off, so that two never talk at once.
   */
  fun toggle(
    enabled: List<String>,
    target: String,
    self: String,
    screenReaders: Set<String>,
  ): List<String> {
    val wasOn = contains(enabled, target)
    val readers = screenReaders.map { normalize(it) }.toSet()
    val result =
      enabled.filterNot { normalize(it) == normalize(self) || normalize(it) == normalize(target) }
    if (wasOn) {
      return result
    }
    val kept =
      if (normalize(target) in readers) result.filterNot { normalize(it) in readers } else result
    return kept + target
  }

  /**
   * The screen readers that must stop before [after] is written over [enabled]: those that are on
   * now and will be off, when [after] turns another screen reader on. Android binds and unbinds the
   * services in any order, and a screen reader that starts before the old one has stopped loses
   * explore by touch when the old one lets go of it. Empty when nothing needs to wait.
   */
  fun mustStopFirst(enabled: List<String>, after: List<String>, screenReaders: Set<String>): List<String> {
    val readers = screenReaders.map { normalize(it) }.toSet()
    val afterSet = after.map { normalize(it) }.toSet()
    val enabledSet = enabled.map { normalize(it) }.toSet()
    val starting = afterSet.any { it in readers && it !in enabledSet }
    if (!starting) {
      return emptyList()
    }
    return enabled.filter { normalize(it) in readers && normalize(it) !in afterSet }
  }

  /** The enabled services with every one of [screenReaders] turned off, for safe mode. */
  fun withoutScreenReaders(enabled: List<String>, screenReaders: Set<String>): List<String> {
    val readers = screenReaders.map { normalize(it) }.toSet()
    return enabled.filterNot { normalize(it) in readers }
  }
}
