package io.doodlebot.backend

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.http.*
import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.mustache.Mustache
import io.ktor.mustache.MustacheContent
import io.ktor.gson.*
import io.ktor.features.*
import io.ktor.request.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

/* Maybe instead:
fun main() {
    embeddedServer(Netty, port = 8080, host = "127.0.0.1", module = Application::module, watchPaths = listOf("/DoodleBotWebBackend/")).start(wait = true)
}
 */

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(Mustache) { mustacheFactory = DefaultMustacheFactory("templates") }
    install(ContentNegotiation) { gson {} }

    routing {
        get("/setup") {
            val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
            val formattedDate = LocalDate.now().format(formatter)
            val config = DoodleConfig(formattedDate)
            call.respond(MustacheContent("frontend.hbs", mapOf("config" to config)))
        }

        post("/genDoodle") {
            val pickedDates: List<LocalDate> = call.receive()
            println(pickedDates)
            call.respond(HttpStatusCode.OK)
        }
    }
}