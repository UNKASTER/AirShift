import java.util.Properties

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.3.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}
localProperties.getProperty("airshift.buildDir")?.let { localBuildRoot ->
    layout.buildDirectory.set(file("$localBuildRoot/root"))
    subprojects {
        layout.buildDirectory.set(file("$localBuildRoot/${project.name}"))
    }
}
