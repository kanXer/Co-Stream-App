plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val googleWebClientId = file("google-services.json")
    .takeIf { it.exists() }
    ?.readText()
    ?.let { text ->
        Regex("\\\"client_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"client_type\\\"\\s*:\\s*3")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
    }
    .orEmpty()

android {
    namespace = "app.costream"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.costream"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("co-stream.jks")
            storePassword = "costream123"
            keyAlias = "costream"
            keyPassword = "costream123"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = true
        shaders = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.animation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)

  // Video playback
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.ui)

  implementation(libs.okhttp)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services.auth)
  implementation(libs.googleid)

  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
