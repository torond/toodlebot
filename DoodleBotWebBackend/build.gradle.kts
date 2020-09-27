import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.10"
    application
}
group = "de.felixfoertsch"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
    maven {
        url = uri("https://dl.bintray.com/kotlin/ktor")
    }
    maven {
        url = uri("https://dl.bintray.com/kotlin/kotlinx")
    }
}
dependencies {
    testImplementation(kotlin("test-junit"))
    implementation("io.ktor:ktor-server-netty:1.4.0")
    implementation("io.ktor:ktor-html-builder:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.2")
}
tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}
application {
    mainClassName = "ServerKt"
}