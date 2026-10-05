import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing comes from SIGNING_* environment variables on CI, or locally from a properties
// file with storeFile, storePassword, keyAlias and keyPassword, named by signing.properties in
// local.properties. Without either, release builds are not signed.
val releaseSigning: Map<String, String>? = run {
    val env = System.getenv()
    if (!env["SIGNING_STORE_FILE"].isNullOrEmpty()) {
        return@run mapOf(
            "storeFile" to env.getValue("SIGNING_STORE_FILE"),
            "storePassword" to env["SIGNING_STORE_PASSWORD"].orEmpty(),
            "keyAlias" to env["SIGNING_KEY_ALIAS"].orEmpty(),
            "keyPassword" to env["SIGNING_KEY_PASSWORD"].orEmpty(),
        )
    }
    val local = rootProject.file("local.properties")
    if (!local.exists()) return@run null
    val path = Properties().apply { local.inputStream().use { load(it) } }
        .getProperty("signing.properties") ?: return@run null
    val file = file(path)
    if (!file.exists()) return@run null
    val props = Properties().apply { file.inputStream().use { load(it) } }
    props.stringPropertyNames().associateWith { props.getProperty(it) }
}

android {
    namespace = "io.github.aaron_gh.shortcutmenu"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.aaron_gh.shortcutmenu"
        // The accessibility shortcut tells an enabled service that it was pressed, instead of
        // turning it off, only on Android 11 and later, for apps that target Android 11 or later.
        minSdk = 30
        targetSdk = 36
        versionCode = 3
        versionName = "0.3"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getValue("storeFile"))
                storePassword = releaseSigning["storePassword"]
                keyAlias = releaseSigning["keyAlias"]
                keyPassword = releaseSigning["keyPassword"]
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigning != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
