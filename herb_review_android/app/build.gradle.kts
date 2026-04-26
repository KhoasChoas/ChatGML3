import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val herbLocalProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
}

fun quoteForBuildConfig(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val herbApiBaseUrl: String = herbLocalProps.getProperty("herbApi.baseUrl", "").trim()
val herbApiLoginEmployeeId: String = herbLocalProps.getProperty("herbApi.loginEmployeeId", "").trim()
val herbApiLoginPassword: String = herbLocalProps.getProperty("herbApi.loginPassword", "").trim()
val herbApiDevPharmacistId: String = herbLocalProps.getProperty("herbApi.devPharmacistId", "").trim()
val herbApiPresetPrescriptionNo: String =
    herbLocalProps.getProperty("herbApi.presetPrescriptionNo", "365624").trim()

android {
    namespace = "com.zhipu.herbreview"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zhipu.herbreview"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "HERB_API_BASE_URL", quoteForBuildConfig(herbApiBaseUrl))
        buildConfigField("String", "HERB_API_LOGIN_EMPLOYEE_ID", quoteForBuildConfig(herbApiLoginEmployeeId))
        buildConfigField("String", "HERB_API_LOGIN_PASSWORD", quoteForBuildConfig(herbApiLoginPassword))
        buildConfigField("String", "HERB_API_DEV_PHARMACIST_ID", quoteForBuildConfig(herbApiDevPharmacistId))
        buildConfigField("String", "HERB_API_PRESET_PRESCRIPTION_NO", quoteForBuildConfig(herbApiPresetPrescriptionNo))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
