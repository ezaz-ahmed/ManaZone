import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing: environment variables (CI) take precedence over local.properties (local builds).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

fun signingValue(env: String, property: String): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(property)?.takeIf { it.isNotBlank() }

val releaseKeystorePath = signingValue("MANAZONE_KEYSTORE_PATH", "manazone.keystore.path")
val releaseKeystorePassword = signingValue("MANAZONE_KEYSTORE_PASSWORD", "manazone.keystore.password")
val releaseKeyAlias = signingValue("MANAZONE_KEY_ALIAS", "manazone.key.alias")
val releaseKeyPassword = signingValue("MANAZONE_KEY_PASSWORD", "manazone.key.password")
val hasReleaseSigning = listOf(releaseKeystorePath, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { it != null }

android {
    namespace = "dev.ezaz.manazone"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.ezaz.manazone"
        minSdk = 30
        targetSdk = 37
        // CI derives these from the release tag; local builds keep the defaults.
        versionCode = providers.environmentVariable("MANAZONE_VERSION_CODE").orNull?.toIntOrNull() ?: 1
        versionName = providers.environmentVariable("MANAZONE_VERSION_NAME").orNull ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Without signing credentials the release APK is built unsigned rather than failing.
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Library translations (Material, etc.) are dead weight for an English-only app.
    androidResources {
        localeFilters += "en"
    }
    packaging {
        resources.excludes += setOf("/META-INF/*.version", "/META-INF/androidx/**", "/kotlin/**", "/DebugProbesKt.bin", "/META-INF/com/android/build/gradle/app-metadata.properties")
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
