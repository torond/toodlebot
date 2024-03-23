import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

//val ktor_version="1.5.2"  // >=1.5.3 throws io.netty.channel.ChannelInitializer - Failed to initialize a channel., java.lang.NoSuchMethodError
val ktor_version="1.6.8"
val kotlin_version="1.7.10"
val logback_version="1.3.14"
val exposed_version="0.39.2"

plugins {
    application
    kotlin("jvm") version "1.7.10"
}

group = "io.toodlebot"
version = "0.1.1-test"

application {
    mainClass.set("io.toodlebot.ApplicationKT")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "io.toodlebot.ApplicationKt"
    }

    // To avoid the duplicate handling strategy error
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // To add all of the dependencies
    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
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
    implementation("com.github.elbekD:kt-telegram-bot:2.2.0")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")
    implementation("org.xerial:sqlite-jdbc:3.34.0")
    implementation("commons-codec:commons-codec:1.15")
    implementation("io.ktor:ktor-server-sessions:$ktor_version")
}

kotlin.sourceSets["main"].kotlin.srcDirs("src")
kotlin.sourceSets["test"].kotlin.srcDirs("test")

sourceSets["main"].resources.srcDirs("resources")
sourceSets["test"].resources.srcDirs("testresources")
