package io.doodlebot
import io.doodlebot.backend.module
import io.doodlebot.bot.setup
import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import kotlin.concurrent.thread


fun main(args: Array<String>) {
    val bot = setup(args[0])
    thread(start=true) {
        bot.start()
    }
    thread(start=true) {
        embeddedServer(Netty, host="0.0.0.0", port = 8088, module = Application::module).start(wait = true)
    } //, watchPaths = listOf("/DoodleBotWebBackend/")
}