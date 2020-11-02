package io.doodlebot.backend

import com.github.mustachejava.DefaultMustacheFactory
import io.doodlebot.backend.model.NewDoodleInfo
import io.doodlebot.backend.service.DatabaseFactory
import io.doodlebot.backend.service.DatabaseService
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.mustache.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*


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
    //install(StatusPages) {}

    DatabaseFactory.init()
    val databaseService = DatabaseService()

    val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    routing {
        /** Endpoint for setting up and editing the initial dates of a Doodle */
        get("/setup") {
            call.respond(MustacheContent("frontend.hbs", mapOf("config" to DoodleConfig.SETUP)))
        }

        get("/setup/{uuid}") {
            // Get dates from DB
            val rawUuid = call.parameters["uuid"]
            val uuid = UUID.fromString(rawUuid)
            val content = databaseService.getDatesByDoodleId(uuid).map { it.doodleDate }
            // Build helper map
            val data = "data" to mapOf("pickedDates" to mapOf("content" to content))
            call.respond(MustacheContent("frontend.hbs", mapOf("config" to DoodleConfig.SETUP, data)))
        }

        /** Accepts setup dates for the Doodle */
        post("/setup") {
            val pickedDatesRaw: List<String> = call.receive()
            val pickedDates = pickedDatesRaw.map { LocalDate.parse(it, inputFormatter) }
            val createdDoodleId = databaseService.addDoodleWithDates(NewDoodleInfo(), pickedDates)
            call.respond(HttpStatusCode.OK, createdDoodleId)
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