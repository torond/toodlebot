plugins {
    kotlin("jvm") version "1.4.10"
    application
}

group = "de.felixfoertsch"
version = "1.0-SNAPSHOT"

repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.elbekD:kt-telegram-bot:1.3.5")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClassName = "MainKt"
}