package io.doodlebot.backend

import com.github.mustachejava.DefaultMustacheFactory
import io.doodlebot.backend.model.NewDoodleInfo
import io.doodlebot.backend.model.NewParticipant
import io.doodlebot.backend.model.Participant
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
import org.jetbrains.exposed.dao.id.EntityID
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

    routing {
        /** Endpoint for setting up and editing the initial dates of a Doodle */
        get("/setup") {
            call.respond(MustacheContent("frontend.hbs", mapOf("config" to DoodleConfig.SETUP)))
        }

        get("/setup/{uuid}") {
            // TODO: When admin removes dates, also remove corresponding participant answers
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
        get("/answer/{uuid}") {
            // TODO: Authentication / recognize user -> For now only one user w/o authentication
            // TODO: This also needs to show already chosen dates, i.e. editing must be possible (auth first and retrieve answers if any)
            // Get dates from DB
            val rawUuid = call.parameters["uuid"]
            val uuid = UUID.fromString(rawUuid)
            val content = databaseService.getDatesByDoodleId(uuid).map { it.doodleDate }
            // Build helper map
            val data = "data" to mapOf("pickableDates" to mapOf("content" to content), "uuid" to rawUuid)
            call.respond(MustacheContent("frontend.hbs", mapOf("config" to DoodleConfig.ANSWER, data)))
        }

        /** Accepts edits to the answers of a Doodle */
        post("/answer/{uuid}") {
            // TODO: This also needs to accept edits on answers
            val rawUuid = call.parameters["uuid"]
            val uuid = UUID.fromString(rawUuid)
            val pickableDates = databaseService.getDatesByDoodleId(uuid)

            val answeredDatesRaw: List<String> = call.receive()
            val answeredDates = answeredDatesRaw.map { LocalDate.parse(it, inputFormatter) }

            // TODO: Check if pickableDates containsAll answeredDates
            // Find answeredDateIds
            val answeredDateIds = databaseService.getDateIdsByDates(answeredDates)

            val participant = NewParticipant("torond")  // TODO: Implement auth, to use real data
            val participantId = databaseService.addParticipantIfNotExisting(participant)

            // Add Participations
            databaseService.addParticipations(uuid, participantId, answeredDateIds)

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