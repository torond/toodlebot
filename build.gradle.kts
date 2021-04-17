import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktor_version="1.4.1"
val kotlin_version="1.4.10"
val logback_version="1.2.1"
val exposed_version="0.27.1"

plugins {
    application
    kotlin("jvm") version "1.4.10"
}

group = "io.doodlebot"
version = "0.0.1"

application {
    mainClassName = "io.doodlebot.ApplicationKt"
}

repositories {
    mavenLocal()
    jcenter()
    maven { url = uri("https://kotlin.bintray.com/ktor") }
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-mustache:$ktor_version")
    implementation("io.ktor:ktor-gson:$ktor_version")
    testImplementation("io.ktor:ktor-server-tests:$ktor_version")
    implementation("com.github.elbekD:kt-telegram-bot:1.3.5")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")
    implementation("org.xerial:sqlite-jdbc:3.30.1")
}

kotlin.sourceSets["main"].kotlin.srcDirs("src")
kotlin.sourceSets["test"].kotlin.srcDirs("test")

sourceSets["main"].resources.srcDirs("resources")
sourceSets["test"].resources.srcDirs("testresources")
