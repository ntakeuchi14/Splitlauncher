plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * 「分割1〜5」を、それぞれ独立したアプリ（別パッケージ）として作るモジュール。
 * 車載HUなどのランチャーはアプリをパッケージ単位で起動するため、
 * 本体アプリ内の別名（activity-alias）では本体画面が開いてしまう。その対策。
 * 各アプリは、本体の SlotEntry にスロット番号を渡して即終了するだけ。
 */
android {
    namespace = "com.example.splitlauncher.slot"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    flavorDimensions += "slot"
    productFlavors {
        for (n in 1..5) {
            create("slot$n") {
                dimension = "slot"
                applicationId = "com.example.splitlauncher.slot$n"
                resValue("string", "slot_label", "分割$n")
                buildConfigField("int", "SLOT", "$n")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
