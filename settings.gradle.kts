pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // 为 gradle/gradle-daemon-jvm.properties 里声明的守护进程 JDK 提供自动下载：
    // Android Studio 自带的 JBR 25 会让 detekt 1.23 崩溃，本机与 CI 统一用 JDK 21。
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AirShift"
include(":app")
