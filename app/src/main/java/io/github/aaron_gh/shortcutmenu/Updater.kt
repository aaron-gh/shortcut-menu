package io.github.aaron_gh.shortcutmenu

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A release on GitHub: its version, notes and APK. [sha256] is the APK's digest, if GitHub gives one. */
data class Release(
  val version: String,
  val notes: String,
  val url: String,
  val sha256: String?,
)

/** Compares version names such as "0.5.1" and "0.6". */
object Versions {
  /** Whether [candidate] is a later version than [current]. Missing parts count as 0. */
  fun isNewer(candidate: String, current: String): Boolean {
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
      val x = a.getOrElse(i) { 0 }
      val y = b.getOrElse(i) { 0 }
      if (x != y) {
        return x > y
      }
    }
    return false
  }

  private fun parts(version: String): List<Int> =
    version.trim().removePrefix("v").split('.').map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
}

/**
 * Updates the app from its GitHub releases, only when asked. Android checks that an update is
 * signed with the key of the installed app, and asks the user to confirm the install.
 */
class Updater(private val context: Context) {
  /** The version installed. */
  val installed: String
    get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

  /** The latest release. Blocks, so not for the main thread. */
  @Throws(IOException::class)
  fun latest(): Release {
    val json = JSONObject(get(LATEST_URL).use { it.readBytes().toString(Charsets.UTF_8) })
    val assets = json.optJSONArray("assets")
    for (i in 0 until (assets?.length() ?: 0)) {
      val asset = assets!!.getJSONObject(i)
      if (asset.optString("name") == APK_NAME) {
        val digest = asset.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 }
        return Release(
          version = json.getString("tag_name").removePrefix("v"),
          notes = json.optString("body").trim(),
          url = asset.getString("browser_download_url"),
          sha256 = digest,
        )
      }
    }
    throw IOException("The latest release has no $APK_NAME.")
  }

  /** Downloads [release]'s APK and checks its digest. Blocks, so not for the main thread. */
  @Throws(IOException::class)
  fun download(release: Release): File {
    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
    val file = File(dir, APK_NAME)
    val digest = MessageDigest.getInstance("SHA-256")
    get(release.url).use { input ->
      file.outputStream().use { out ->
        val buffer = ByteArray(BUFFER)
        while (true) {
          val n = input.read(buffer)
          if (n < 0) break
          digest.update(buffer, 0, n)
          out.write(buffer, 0, n)
        }
      }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    if (release.sha256 != null && !release.sha256.equals(actual, ignoreCase = true)) {
      file.delete()
      throw IOException("The download does not match its checksum.")
    }
    return file
  }

  /** Hands [apk] to Android's installer, which asks the user to confirm. */
  @Throws(IOException::class)
  fun install(apk: File) {
    val installer = context.packageManager.packageInstaller
    val params =
      PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(context.packageName)
      }
    val id = installer.createSession(params)
    installer.openSession(id).use { session ->
      apk.inputStream().use { input ->
        session.openWrite(APK_NAME, 0, apk.length()).use { out ->
          input.copyTo(out)
          session.fsync(out)
        }
      }
      // Mutable, since the installer adds the status to the intent.
      val result =
        PendingIntent.getBroadcast(
          context,
          id,
          Intent(context, InstallResultReceiver::class.java),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
      session.commit(result.intentSender)
    }
  }

  /** Whether Android lets this app install packages. If not, the user has to allow it. */
  fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

  private fun get(url: String): java.io.InputStream {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = TIMEOUT_MS
    connection.readTimeout = TIMEOUT_MS
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
      throw IOException("GitHub answered ${connection.responseCode}.")
    }
    return connection.inputStream
  }

  private companion object {
    const val LATEST_URL = "https://api.github.com/repos/aaron-gh/shortcut-menu/releases/latest"
    const val APK_NAME = "shortcut-menu.apk"
    const val TIMEOUT_MS = 15_000
    const val BUFFER = 64 * 1024
  }
}

/** Receives the installer's result: asks the user to confirm, or says why the install failed. */
class InstallResultReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
      }
      PackageInstaller.STATUS_SUCCESS -> Unit
      else -> {
        val reason = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        Toast.makeText(
            context,
            context.getString(R.string.update_failed, reason),
            Toast.LENGTH_LONG,
          )
          .show()
      }
    }
  }
}
