plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    // 默认规则集；既有发现记入基线，此后新增的复杂度/长函数/长参数列表等问题让 detekt 失败。
    buildUponDefaultConfig = true
    // detekt.yml 只对 @Composable 放开命名/长度/魔法数字规则，见文件内注释。
    config.setFrom(file("detekt.yml"))
    baseline = file("detekt-baseline.xml")
    source.setFrom("src/main/java", "src/test/java", "src/androidTest/java")
}

// detekt 1.23 按运行 Gradle 的 JDK 推断 jvm-target，Android Studio 自带的 JDK 25 超出其上限；
// 与 compileOptions 一致地固定为 17。
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach { jvmTarget = "17" }
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach { jvmTarget = "17" }

// 应用版本。仪器测试会把 Debug 版装到手机上、覆盖日常用的 release：两者 versionCode 相同时 vivo 安装器把
// release 当“相同版本”，`adb install -r` 只会失败，只能在手机弹框里手工点“重新安装”。因此 Debug 变体用
// 更高的固定偏移与 -debug 后缀（见下方 androidComponents）：测试装 Debug 是升级；装回 release 用
// `adb install -r -d`——对已装的可调试包允许降级——两个方向都不再撞“相同版本”。
val appVersionCode = 53
val appVersionName = "0.13.1"
val debugVersionCodeOffset = 1_000_000

android {
    namespace = "com.bradj.airshift"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bradj.airshift"
        minSdk = 33
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 个人使用：用 debug 签名让 release 可直接安装，日常与取证都用它（R8、非 debuggable）。
            signingConfig = signingConfigs.getByName("debug")
            // 允许 shell 侧 profiling / 应用层 Trace 段被 Perfetto 收集；不放开调试。
            isProfileable = true
            // 开启压缩是为了把 POI/OpenCV/ONNX 的体积问题与 keep 规则提前在 assembleRelease 上暴露。规则见 proguard-rules.pro。
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

// Debug 变体的版本号：versionCode 加固定偏移、versionName 加 -debug，理由见文件顶部。
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.outputs.forEach { output ->
            output.versionCode.set(appVersionCode + debugVersionCodeOffset)
            output.versionName.set("$appVersionName-debug")
        }
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
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
