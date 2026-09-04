plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    // 默认规则集；既有发现记入基线，此后新增的复杂度/长函数/长参数列表等问题让 detekt 失败。
    buildUponDefaultConfig = true
    baseline = file("detekt-baseline.xml")
    source.setFrom("src/main/java", "src/test/java", "src/androidTest/java")
}

// detekt 1.23 按运行 Gradle 的 JDK 推断 jvm-target，Android Studio 自带的 JDK 25 超出其上限；
// 与 compileOptions 一致地固定为 17。
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach { jvmTarget = "17" }
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach { jvmTarget = "17" }

android {
    namespace = "com.bradj.airshift"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bradj.airshift"
        minSdk = 33
        targetSdk = 37
        versionCode = 44
        versionName = "0.10.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 未配置发布签名，release 仍不可直接安装；开启压缩是为了把 POI/OpenCV/ONNX 的体积问题
            // 与 keep 规则提前在 assembleRelease 上暴露。规则见 proguard-rules.pro。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        // 既有 28 条 warning 记入基线；此后新增的 warning 直接让 lintDebug 失败。
        baseline = file("lint-baseline.xml")
        warningsAsErrors = true
        abortOnError = true
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
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
