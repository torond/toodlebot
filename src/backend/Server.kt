package io.doodlebot.backend

import com.github.mustachejava.DefaultMustacheFactory
import io.doodlebot.backend.model.NewDoodleInfo
import io.doodlebot.backend.model.NewParticipant
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

const val TEMPLATE_PATH = "templates"
const val TEMPLATE_NAME = "frontend.mustache"

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

/* Maybe instead:
fun main() {
    embeddedServer(Netty, port = 8080, host = "127.0.0.1", module = Application::module, watchPaths = listOf("/DoodleBotWebBackend/")).start(wait = true)
}
 */

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(Mustache) { mustacheFactory = DefaultMustacheFactory(TEMPLATE_PATH) }
    install(ContentNegotiation) { gson {} }
    //install(StatusPages) {}

    DatabaseFactory.init()
    val databaseService = DatabaseService()

    val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // TODO: Correct Exceptions and Status Code
    fun ApplicationCall.getUuid(): UUID = UUID.fromString(this.parameters["uuid"])


    //pickableDates by doodleId (dates enabled in the calendar)
    // -> /setup/{uuid}: All dates
    // -> /answer/{uuid}: Dates chosen by admin on setup -> proposedDates
    // -> /close/{uuid}: Dates chosen by admin on setup -> proposedDates
    // -> /view/{uuid}: none
    //pickedDates by doodleId & participantId (prev. picked dates for updating doodle or answers)
    // -> /setup/{uuid}: Dates prev. chosen by admin -> proposedDates
    // -> /answer/{uuid}: Dates prev. chosen by participant -> yesDates
    //finalDates by doodleId
    // -> /close/{uuid}: Final dates prev. chosen by admin -> finalDates
    // -> /view/{uuid}: Final dates prev. chosen by admin -> finalDates
    //
    // => Mustache Template anpassen, s.d. Server.kt verständlich bleibt

    // MAPPING
    // (0. All dates / no dates -> controlled by DoodleConfig or absence of 1.)
    // 1. Dates chosen by admin on setup, open for answers -> proposedDates in backend, enabledDates or defaultDates in frontend
    // 2. Dates chosen by participants -> yesDates in backend, defaultDates in frontend (other: chosenDates, answeredDates, repliedDates, respondedDates, consideredDates, committedDates, attendableDates)
    // 3. Final dates chosen by admin -> finalDates in backend and frontend


    /*
    * getDatesByDoodleId
    * addDoodleWithDates
    * getDateIdsByDates
    * addParticipantIfNotExisting
    * addParticipations
    * getParticipations
    * getDoodleByUuid (for numOfParts)
    * markDatesAsFinal
    * markDoodleAsClosed
    * getFinalDatesByDoodleId
    * */

    fun buildMustacheMapping(config: DoodleConfig,
                             enabledDates: List<LocalDate>? = null,
                             defaultDates: List<LocalDate>? = null,
                             finalDates: List<LocalDate>? = null,
                             numberOfParticipants: Int? = null,
                             participations: Map<LocalDate, List<EntityID<Int>>>? = null,
                             uuid: UUID? = null): Map<String, Any> {
        val mappings: MutableList<Pair<String, Any>> = mutableListOf<Pair<String, Any>>()
        mappings.add("config" to config)
        if (uuid != null) mappings.add("uuid" to uuid)
        if (enabledDates != null) mappings.add("enabledDates" to mapOf("content" to enabledDates))
        if (defaultDates != null) mappings.add("defaultDates" to mapOf("content" to defaultDates))  // Maybe add ifNotNull
        if (finalDates != null) mappings.add("finalDates" to mapOf("content" to finalDates))
        if (numberOfParticipants != null) mappings.add("numberOfParticipants" to numberOfParticipants)
        if (participations != null) mappings.add("participations" to mapOf("content" to participations.entries))
        return mappings.map { it.first to it.second }.toMap()
    }

    routing {
        /** Endpoint for setting up and editing the initial dates of a Doodle */
        get("/setup") {
            call.respond(MustacheContent(TEMPLATE_NAME, mapOf("config" to DoodleConfig.SETUP)))
        }

        get("/setup/{uuid}") {
            // TODO: When admin removes dates, also remove corresponding participant answers
            val uuid: UUID = call.getUuid()
            val proposedDates = databaseService.getDatesByDoodleId(uuid).map { it.doodleDate }
            val mustacheMapping = buildMustacheMapping(DoodleConfig.SETUP, defaultDates = proposedDates, uuid = uuid)
            call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
        }

        /** Accepts setup dates for the Doodle */
        post("/setup") {
            val pickedDatesRaw: List<String> = call.receive()
            val pickedDates = pickedDatesRaw.map { LocalDate.parse(it, inputFormatter) }
            val createdDoodleId = databaseService.addDoodleWithDates(NewDoodleInfo(), pickedDates)
            call.respond(HttpStatusCode.OK, createdDoodleId)
        }

        // TODO: Add /setup/{uuid} endpoint for updating Doodles!

        /** Endpoint for answering and editing the answers of a Doodle */
        get("/answer/{uuid}") {
            // TODO: Authentication / recognize user -> For now only one user w/o authentication
            // TODO: This also needs to show already chosen dates (defaultDates = yesDates), i.e. editing must be possible (auth first and retrieve answers if any)
            // TODO: Get Participations (of all users) and show them in the calendar
            // -> databaseService.getParticipationsOrEmptyList(uuid, participantId) -> Give this list to the Mustache Template
            // Get dates from DB
            val uuid = call.getUuid()
            val proposedDates = databaseService.getDatesByDoodleId(uuid).map { it.doodleDate }
            val mustacheMapping = buildMustacheMapping(DoodleConfig.ANSWER, enabledDates = proposedDates, uuid = uuid)
            call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
        }

        /** Accepts edits to the answers of a Doodle */
        post("/answer/{uuid}") {
            val uuid = call.getUuid()
            val pickableDates = databaseService.getDatesByDoodleId(uuid)

            val answeredDatesRaw: List<String> = call.receive()
            val answeredDates = answeredDatesRaw.map { LocalDate.parse(it, inputFormatter) }

            // TODO: Check if pickableDates containsAll answeredDates
            // Find answeredDateIds
            val answeredDateIds = databaseService.getDateIdsByDates(answeredDates)

            val participant = NewParticipant(LocalTime.now().toString())  // TODO: Implement auth, to use real data
            val participantId = databaseService.addParticipantIfNotExisting(participant)

            // Add Participations
            // TODO: This also needs to accept edits on answers
            // -> databaseService.updateParticipations(uuid, participantId, answeredDateIds)
            databaseService.addParticipations(uuid, participantId, answeredDateIds)

            call.respond(HttpStatusCode.OK)
        }

        /** Endpoint for closing a Doodle */
        get("/close/{uuid}") {
            // Needs: pickableDates, Participations
            val uuid = call.getUuid()
            val proposedDates = databaseService.getDatesByDoodleId(uuid).map { it.doodleDate }
            val participations = databaseService.getParticipations(uuid)
            val numberOfParticipants = databaseService.getDoodleByUuid(uuid).numberOfParticipants
            // TODO: Show closed dates to accept for editing? (defaultDates = finalDates)
            val mustacheMapping = buildMustacheMapping(DoodleConfig.CLOSE,
                    enabledDates = proposedDates,
                    numberOfParticipants = numberOfParticipants,
                    participations = participations,
                    uuid = uuid)
            call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
        }

        /** Accepts final dates of a Doodle*/
        post("/close/{uuid}") {
            val uuid = call.getUuid()
            val pickedDatesRaw: List<String> = call.receive()
            val pickedDates = pickedDatesRaw.map { LocalDate.parse(it, inputFormatter) }
            // Mark final dates
            val dateIds = databaseService.getDateIdsByDates(pickedDates)
            databaseService.markDatesAsFinal(uuid, dateIds)
            // Set Doodle state to closed
            databaseService.markDoodleAsClosed(uuid)

            // Redirect admin to /view/{uuid}
            call.respondRedirect("/view/${uuid}")
            //call.respond(HttpStatusCode.OK)
        }

        /** Endpoint for viewing the results of a Doodle*/
        get("/view/{uuid}") {
            // TODO: Check if Doodle is closed
            // TODO: Show own chosen Dates? (yesDates)
            val uuid = call.getUuid()
            val participations = databaseService.getParticipations(uuid)
            val numberOfParticipants = databaseService.getDoodleByUuid(uuid).numberOfParticipants
            val proposedDates = databaseService.getDatesByDoodleId(uuid).map { it.doodleDate }
            val finalDates = databaseService.getFinalDatesByDoodleId(uuid).map { it.doodleDate }
            val mustacheMapping = buildMustacheMapping(DoodleConfig.VIEW,
                    enabledDates = proposedDates,
                    finalDates = finalDates,
                    numberOfParticipants = numberOfParticipants,
                    participations = participations,
                    uuid = uuid)
            call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
        }
    }
}