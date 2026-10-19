import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val rawTagVersion = (project.findProperty("versionName") as? String)
    ?: System.getenv("GITHUB_REF_NAME")?.removePrefix("v")
    ?: "1.2.0"

val cleanVersionName = if (rawTagVersion.matches(Regex("""^\d+\.\d+\.\d+.*"""))) {
    rawTagVersion
} else {
    "1.2.0"
}

fun computeDeterministicVersionCode(ver: String): Int {
    val core = ver.substringBefore("-").split(".")
    val major = core.getOrNull(0)?.toIntOrNull() ?: 1
    val minor = core.getOrNull(1)?.toIntOrNull() ?: 2
    val patch = core.getOrNull(2)?.toIntOrNull() ?: 0
    return (major * 10000 + minor * 100 + patch).coerceAtLeast(1)
}

android {
    namespace = "com.example.volumeboost"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.mrquster.hyperboost"
        minSdk = 29
        targetSdk = 35
        versionCode = computeDeterministicVersionCode(cleanVersionName)
        versionName = cleanVersionName

        vectorDrawables {
            useSupportLibrary = true
        }
        resourceConfigurations += listOf("ru", "en")
    }

    signingConfigs {
        create("release") {
            val envKeystorePath = System.getenv("KEYSTORE_FILE")
            val envStorePassword = System.getenv("KEYSTORE_PASSWORD")
            val envKeyAlias = System.getenv("KEY_ALIAS")
            val envKeyPassword = System.getenv("KEY_PASSWORD")

            if (!envKeystorePath.isNullOrBlank() && !envStorePassword.isNullOrBlank()) {
                storeFile = file(envKeystorePath)
                storePassword = envStorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
            } else if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val relSign = signingConfigs.getByName("release")
            if (relSign.storeFile?.exists() == true) {
                signingConfig = relSign
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
