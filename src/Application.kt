package io.doodlebot
import io.doodlebot.backend.module
import io.doodlebot.backend.service.Env
import io.doodlebot.bot.setup
import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.util.*
import kotlin.concurrent.thread


fun main() {
    /*thread(start=true) {
        bot.start()
    }*/
    Env  // To make sure that Env is initialized before anything else, e.g. HashUtil
    thread(start=true) {
        embeddedServer(Netty, host="0.0.0.0", port = 8088, module = Application::module).start(wait = true)
    } //, watchPaths = listOf("/DoodleBotWebBackend/")
}