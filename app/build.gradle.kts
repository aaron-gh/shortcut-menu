plugins {
    id("com.android.application")
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
        versionCode = 1
        versionName = "0.1"
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
