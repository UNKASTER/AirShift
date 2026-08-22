plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass = "com.bradj.airshift.gateway.MainKt"
}

dependencies {
    implementation("org.json:json:20260814")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
