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
        /** Endpoint for setting up and editing the initial dates of a Doodle */
        get("/setup") {
            // TODO: Generate Crypto URL and redirect
            call.respond(MustacheContent("frontend.hbs", mapOf("config" to DoodleConfig.SETUP)))
        }

        /** Accepts setup dates for the Doodle */
        post("/setup") {
            val pickedDates: List<LocalDate> = call.receive()
            // TODO: Persist dates
            call.respond(HttpStatusCode.OK)
        }

        /** Endpoint for answering and editing the answers of a Doodle */
        get("/edit") {
            // TODO: Accept Crypto URL
            // TODO: Authentication / recognize user
            call.respond(MustacheContent("frontend.hbs", mapOf("config" to DoodleConfig.EDIT)))
        }

        /** Accepts edits to the answers of a Doodle */
        post("/edit") {
            call.respond(HttpStatusCode.OK)
        }

        /** Endpoint for closing a Doodle */
        get("/close") {
            call.respond(MustacheContent("frontend.hbs", mapOf("config" to DoodleConfig.CLOSE)))
        }

        /** Accepts final dates of a Doodle*/
        post("/close") {
            call.respond(HttpStatusCode.OK)
        }

        /** Endpoint for viewing the results of a Doodle*/
        get("/view") {
            call.respond(MustacheContent("frontend.hbs", mapOf("config" to DoodleConfig.VIEW)))
        }
    }
}