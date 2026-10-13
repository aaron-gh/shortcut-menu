package io.github.aaron_gh.shortcutmenu

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Shows how to set Shortcut Menu up, and which services it offers. */
class SettingsActivity : Activity() {
  private lateinit var services: Services
  private lateinit var content: LinearLayout
  private lateinit var updater: Updater
  private lateinit var updateStatus: TextView
  private lateinit var updateButton: Button
  /** What the last check for an update found, kept across rebuilding the screen. */
  private var release: Release? = null
  private var updateStatusText = ""
  private var updateButtonText: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    services = Services(this)
    updater = Updater(this)
    val padding = dp(16)
    content =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padding, padding, padding, padding)
      }
    setContentView(
      ScrollView(this).apply {
        fitsSystemWindows = true
        addView(content)
      }
    )
  }

  override fun onResume() {
    super.onResume()
    // The permission, the shortcut and the installed services can all change while away.
    build()
  }

  private fun build() {
    content.removeAllViews()
    paragraph(getString(R.string.settings_intro))

    heading(R.string.settings_permission_heading)
    if (services.canWrite()) {
      paragraph(getString(R.string.settings_permission_granted))
    } else {
      paragraph(getString(R.string.settings_permission_needed))
      val command = grantCommand()
      paragraph(command).setTextIsSelectable(true)
      button(R.string.settings_copy_command) {
        getSystemService(ClipboardManager::class.java)
          .setPrimaryClip(ClipData.newPlainText(command, command))
        Toast.makeText(this, R.string.settings_copied, Toast.LENGTH_SHORT).show()
      }
    }

    heading(R.string.settings_shortcut_heading)
    paragraph(getString(R.string.settings_shortcut_help))
    if (services.skipShortcutQuestion()) {
      paragraph(getString(R.string.settings_question_skipped))
    }
    paragraph(shortcutStatus(R.string.settings_volume_keys, KEY_VOLUME_SHORTCUT))
    paragraph(shortcutStatus(R.string.settings_button, KEY_BUTTON_SHORTCUT))
    button(R.string.settings_open_accessibility) {
      startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    heading(R.string.settings_choices_heading)
    paragraph(getString(R.string.settings_choices_help))
    content.addView(
      CheckBox(this).apply {
        setText(R.string.settings_engine_menu)
        isChecked = services.engineMenu()
        setOnCheckedChangeListener { _, checked -> services.setEngineMenu(checked) }
      },
      matchWidth(),
    )
    val chosen = services.chosen().map { ServiceList.normalize(it) }.toMutableSet()
    for (entry in services.installed()) {
      content.addView(
        CheckBox(this).apply {
          text =
            if (entry.screenReader) getString(R.string.settings_screen_reader, entry.label)
            else entry.label
          isChecked = ServiceList.normalize(entry.component) in chosen
          setOnCheckedChangeListener { _, checked ->
            val key = ServiceList.normalize(entry.component)
            if (checked) chosen.add(key) else chosen.remove(key)
            services.setChosen(chosen.toSet())
          }
        },
        matchWidth(),
      )
    }

    heading(R.string.settings_try_heading)
    button(R.string.settings_try) { MenuActivity.open(this) }

    heading(R.string.settings_update_heading)
    paragraph(getString(R.string.settings_update_installed, updater.installed))
    button(R.string.settings_update_check) { checkForUpdate() }
    updateStatus =
      paragraph(updateStatusText).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
      }
    updateButton = button(updateButtonText ?: "") { installUpdate() }
    updateButton.visibility = if (release != null) View.VISIBLE else View.GONE
  }

  /** Asks GitHub for the latest release, when the user chooses to, and says what it found. */
  private fun checkForUpdate() {
    setUpdateStatus(getString(R.string.update_checking))
    Thread {
        try {
          val latest = updater.latest()
          runOnUiThread {
            if (Versions.isNewer(latest.version, updater.installed)) {
              release = latest
              updateButtonText = getString(R.string.update_install, latest.version)
              updateButton.text = updateButtonText
              updateButton.visibility = View.VISIBLE
              setUpdateStatus(getString(R.string.update_available, latest.version, latest.notes))
            } else {
              release = null
              updateButton.visibility = View.GONE
              setUpdateStatus(getString(R.string.update_current, updater.installed))
            }
          }
        } catch (e: Exception) {
          runOnUiThread { setUpdateStatus(getString(R.string.update_error, e.message.orEmpty())) }
        }
      }
      .start()
  }

  /** Downloads the release that the check found, and hands it to Android to install. */
  private fun installUpdate() {
    val latest = release ?: return
    if (!updater.canInstall()) {
      setUpdateStatus(getString(R.string.update_allow))
      startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
      )
      return
    }
    setUpdateStatus(getString(R.string.update_downloading, latest.version))
    updateButton.isEnabled = false
    Thread {
        try {
          updater.install(updater.download(latest))
          runOnUiThread { setUpdateStatus(getString(R.string.update_confirm)) }
        } catch (e: Exception) {
          runOnUiThread { setUpdateStatus(getString(R.string.update_error, e.message.orEmpty())) }
        }
        runOnUiThread { updateButton.isEnabled = true }
      }
      .start()
  }

  private fun setUpdateStatus(text: String) {
    updateStatusText = text
    updateStatus.text = text
  }

  /** Says what a shortcut is assigned to, and warns when Android would ask which one to use. */
  private fun shortcutStatus(name: Int, key: String): String {
    val targets = services.shortcutTargets(key)
    val shortcut = getString(name)
    val hasSelf = ServiceList.contains(targets, services.self)
    return when {
      targets.isEmpty() -> getString(R.string.settings_shortcut_unused, shortcut)
      hasSelf && targets.size == 1 -> getString(R.string.settings_shortcut_ready, shortcut)
      else -> {
        val names = targets.joinToString(", ") { services.labelOf(it) }
        if (hasSelf) getString(R.string.settings_shortcut_shared, shortcut, names)
        else getString(R.string.settings_shortcut_other, shortcut, names)
      }
    }
  }

  private fun grantCommand(): String =
    "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"

  private fun heading(text: Int) {
    content.addView(
      TextView(this).apply {
        setText(text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        isAccessibilityHeading = true
        setPadding(0, dp(24), 0, dp(8))
      },
      matchWidth(),
    )
  }

  private fun paragraph(text: String): TextView {
    val view =
      TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setPadding(0, 0, 0, dp(8))
      }
    content.addView(view, matchWidth())
    return view
  }

  private fun button(text: Int, onClick: () -> Unit): Button = button(getString(text), onClick)

  private fun button(text: String, onClick: () -> Unit): Button {
    val view =
      Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
      }
    content.addView(view, matchWidth())
    return view
  }

  private fun matchWidth() =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

  private fun dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics)
      .toInt()

  private companion object {
    // Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE and ACCESSIBILITY_BUTTON_TARGETS, which
    // the SDK hides.
    const val KEY_VOLUME_SHORTCUT = "accessibility_shortcut_target_service"
    const val KEY_BUTTON_SHORTCUT = "accessibility_button_targets"
  }
}
