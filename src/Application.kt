package io.toodlebot
import io.toodlebot.backend.module
import io.toodlebot.backend.service.Env
import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlin.concurrent.thread


fun main() {
    /*thread(start=true) {
        bot.start()
    }*/
    Env  // To make sure that Env is initialized before anything else, e.g. HashUtil
    thread(start=true) {
        embeddedServer(Netty, host="0.0.0.0", port = Env.port.toInt(), module = Application::module).start(wait = true)
    } //, watchPaths = listOf("/ToodleBotWebBackend/")
}