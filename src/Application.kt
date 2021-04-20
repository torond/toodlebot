package io.doodlebot
import io.doodlebot.backend.module
import io.doodlebot.bot.setup
import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.util.*
import kotlin.concurrent.thread


fun main() {
    // Load properties
    /*val props = Properties()
    val inputStream = FileInputStream("environment.properties")
    props.load(inputStream)

    val bot = setup(
        props.getProperty("bot_name"),
        props.getProperty("bot_token"),
        props.getProperty("local_ip")
    )
    thread(start=true) {
        bot.start()
    }*/
    thread(start=true) {
        embeddedServer(Netty, host="0.0.0.0", port = 8088, module = Application::module).start(wait = true)
    } //, watchPaths = listOf("/DoodleBotWebBackend/")
}