plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.neo.neomovies"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.neo.neomovies"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"https://api.neomovies.ru\"")
        buildConfigField("String", "NEO_ID_BASE_URL", "\"https://id.neomovies.ru\"")

        fun readDotEnv(key: String): String? {
            val envFile = rootProject.file(".env")
            if (!envFile.exists()) return null

            return envFile.readLines()
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx <= 0) return@mapNotNull null
                    val k = line.substring(0, idx).trim()
                    val v = line.substring(idx + 1).trim().trim('"').trim('\'')
                    if (k == key) v else null
                }
                .firstOrNull()
        }

        val neoIdApiKey =
            (project.findProperty("NEO_ID_API_KEY") as String?)
                ?: System.getenv("NEO_ID_API_KEY")
                ?: readDotEnv("NEO_ID_API_KEY")
                ?: ""
        buildConfigField("String", "NEO_ID_API_KEY", "\"$neoIdApiKey\"")
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "PRE_RELEASE", "false")
        }

        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "PRE_RELEASE", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("prerelease") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
            buildConfigField("boolean", "PRE_RELEASE", "true")
            versionNameSuffix = "-pre"
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    implementation(libs.coil.compose)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation("androidx.browser:browser:1.8.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
