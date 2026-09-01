plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.bradj.airshift"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bradj.airshift"
        minSdk = 33
        targetSdk = 37
        versionCode = 9
        versionName = "0.4.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets["androidTest"].assets.directories.add(rootProject.file("testdata").absolutePath)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.android.gms:play-services-location:21.4.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.21.1")
    implementation("org.opencv:opencv:4.12.0")
    implementation("org.apache.poi:poi:5.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
