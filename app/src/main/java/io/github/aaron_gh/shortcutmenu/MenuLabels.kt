package io.github.aaron_gh.shortcutmenu

/** Names for the services in the menu, told apart when two have the same name. */
object MenuLabels {
  /** A service's own name, and the package of the app it comes from. */
  data class Service(val label: String, val packageName: String)

  /**
   * Returns a name for each of [services], in order. A name that two or more services share gets
   * the maker from the package name added, such as "TalkBack (Samsung)" for
   * com.samsung.android.accessibility.talkback. If that still leaves them the same, the whole
   * package name is added instead.
   */
  fun distinct(services: List<Service>): List<String> {
    val sameLabel = services.groupingBy { it.label }.eachCount()
    val withMaker = services.map { if (sameLabel.getValue(it.label) > 1) "${it.label} (${maker(it.packageName)})" else it.label }
    val sameMaker = withMaker.groupingBy { it }.eachCount()
    return services.mapIndexed { index, service ->
      if (sameMaker.getValue(withMaker[index]) > 1) "${service.label} (${service.packageName})"
      else withMaker[index]
    }
  }

  /**
   * Whether Pico, which reads only the Latin alphabet, can say anything of [label]: whether it has a
   * Latin letter or a digit.
   */
  fun speakable(label: String): Boolean =
    label.codePoints().anyMatch {
      it in '0'.code..'9'.code ||
        (Character.isLetter(it) && Character.UnicodeScript.of(it) == Character.UnicodeScript.LATIN)
    }

  /** The maker in a package name: the part after the top-level domain, such as "Samsung". */
  fun maker(packageName: String): String {
    val parts = packageName.split('.').filter { it.isNotEmpty() }
    val name = if (parts.size >= 2) parts[1] else parts.firstOrNull() ?: packageName
    return name.replaceFirstChar { it.uppercase() }
  }
}
