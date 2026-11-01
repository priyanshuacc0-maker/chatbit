plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes these in so every build gets a higher versionCode than the last one.
// Local builds fall back to the values below.
val overrideVersionCode = (findProperty("versionCodeOverride") as String?)?.toIntOrNull()
val overrideVersionName = findProperty("versionNameOverride") as String?

android {
    namespace = "com.meshline.chat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.meshline.chat"
        minSdk = 26
        targetSdk = 35
        versionCode = overrideVersionCode ?: 3
        versionName = overrideVersionName ?: "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // play-services-nearby 19.5.0 ships Kotlin metadata from a newer
        // compiler than the 2.0.21 used here. The API surface we call is
        // plain Java-style, so skipping the version check is safe and keeps
        // the dependency current.
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // MainActivity calls registerForActivityResult without any Fragment
        // dependency. Lint's InvalidFragmentVersionForActivityResult check
        // only makes sense for FragmentActivity users, so it misfires here
        // and fails the release build.
        disable += "InvalidFragmentVersionForActivityResult"
    }

    // Release signing. The keystore and its passwords come from environment
    // variables that CI fills from repository secrets; nothing secret is ever
    // committed. If they are absent (a plain local build), no release signing
    // config is attached and only debug builds are usable.
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("MESHLINE_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("MESHLINE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MESHLINE_KEY_ALIAS")
                keyPassword = System.getenv("MESHLINE_KEY_PASSWORD")
                storeType = System.getenv("MESHLINE_KEYSTORE_TYPE")
                    ?: if (ksPath.endsWith(".p12", ignoreCase = true)) "PKCS12" else "JKS"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.gms:play-services-nearby:19.5.0")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
