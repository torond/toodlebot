package io.doodlebot.backend

import com.github.mustachejava.DefaultMustacheFactory
import io.doodlebot.backend.model.NewParticipant
import io.doodlebot.backend.service.DatabaseFactory
import io.doodlebot.backend.service.DatabaseService
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.features.ContentTransformationException
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
import java.time.format.DateTimeParseException
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
    install(StatusPages) {
        exception<BadRequestException> { cause ->
            call.respond(HttpStatusCode.BadRequest) // If request is missing doodleId or dates
            log.warn(cause.message)
        }
        exception<NotFoundException> { cause ->
            call.respond(HttpStatusCode.NotFound)  // If Doodle or dates are not found
            log.warn(cause.message)
        }
        exception<DateTimeParseException> { cause ->
            call.respond(HttpStatusCode.BadRequest)
            log.warn(cause.message)
        }
        exception<ContentTransformationException> { cause ->
            call.respond(HttpStatusCode.BadRequest)  // If content cannot be negotiated
            log.warn(cause.message)
        }
        exception<IllegalArgumentException> { cause ->
            call.respond(HttpStatusCode.BadRequest)  // If content cannot be negotiated
            log.warn(cause.message)
        }
        exception<IllegalStateException> { cause ->
            call.respond(HttpStatusCode.Forbidden)
            log.warn(cause.message)
        }
        // UnauthorizedException if /setup/{doodleId} is queried by non-admin
    }

    DatabaseFactory.init()
    val databaseService = DatabaseService()
    val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun ApplicationCall.getDoodleId(): UUID {
        this.parameters["doodleId"] ?: throw BadRequestException("Must provide id")
        return UUID.fromString(this.parameters["doodleId"])
    }
    fun ApplicationCall.getDoodleIdOrNull(): UUID? {
        return this.parameters["doodleId"]?.let { UUID.fromString(it) }
    }
    suspend fun ApplicationCall.getDates(): List<LocalDate> {
        val raw = this.receiveOrNull<List<String>>() ?: throw BadRequestException("Must provide dates")
        return raw.map { LocalDate.parse(it, inputFormatter) }
    }

    //pickableDates by doodleId (dates enabled in the calendar)
    // -> /setup/{doodleId}: All dates
    // -> /answer/{doodleId}: Dates chosen by admin on setup -> proposedDates
    // -> /close/{doodleId}: Dates chosen by admin on setup -> proposedDates
    // -> /view/{doodleId}: none
    //pickedDates by doodleId & participantId (prev. picked dates for updating doodle or answers)
    // -> /setup/{doodleId}: Dates prev. chosen by admin -> proposedDates
    // -> /answer/{doodleId}: Dates prev. chosen by participant -> yesDates
    //finalDates by doodleId
    // -> /close/{doodleId}: Final dates prev. chosen by admin -> finalDates
    // -> /view/{doodleId}: Final dates prev. chosen by admin -> finalDates
    //
    // => Mustache Template anpassen, s.d. Server.kt verständlich bleibt

    // MAPPING
    // (0. All dates / no dates -> controlled by DoodleConfig or absence of 1.)
    // 1. Dates chosen by admin on setup, open for answers -> proposedDates in backend, enabledDates or defaultDates in frontend
    // 2. Dates chosen by participants -> yesDates in backend, defaultDates in frontend (other: chosenDates, answeredDates, repliedDates, respondedDates, consideredDates, committedDates, attendableDates)
    // 3. Final dates chosen by admin -> finalDates in backend and frontend

    /*
    * Principle of least astonishment.
    * Be precise.
    * -> Mention parameters (byDoodleId), but not in add methods?
    * -> ID's always with corresponding Table (DoodleId not Uuid)
    *
    * -> No EntityId<...> in Server.kt, at most the doodle doodleId
    * -> No other IDs? Yes.
    *
    * ideas:
    * - only update methods -> addOrUpdate
    *
    * questions: Optional return types?
    * */

    fun buildMustacheMapping(config: DoodleConfig,
                             enabledDates: List<LocalDate>? = null,
                             defaultDates: List<LocalDate>? = null,
                             finalDates: List<LocalDate>? = null,
                             numberOfParticipants: Int? = null,
                             participations: Map<LocalDate, List<EntityID<Int>>>? = null,
                             doodleId: UUID? = null): Map<String, Any> {
        val mappings: MutableList<Pair<String, Any>> = mutableListOf()
        mappings.add("config" to config)
        if (doodleId != null) mappings.add("doodleId" to doodleId)
        if (enabledDates != null) mappings.add("enabledDates" to mapOf("content" to enabledDates))
        if (defaultDates != null) mappings.add("defaultDates" to mapOf("content" to defaultDates))  // Maybe add ifNotNull
        if (finalDates != null) mappings.add("finalDates" to mapOf("content" to finalDates))
        if (numberOfParticipants != null) mappings.add("numberOfParticipants" to numberOfParticipants)
        if (participations != null) mappings.add("participations" to mapOf("content" to participations.entries))
        return mappings.map { it.first to it.second }.toMap()
    }

    routing {
        intercept(ApplicationCallPipeline.Call) {
            this.call.getDoodleIdOrNull()?.let {
                println(this.call.request.uri)
                if (!this.call.request.uri.startsWith("/view") && databaseService.doodleIsClosed(it)) {
                    this.call.respondRedirect("/view/$it")
                    this.finish()
                }
            }
        }

        /** Endpoint for setting up and editing the initial dates of a Doodle */
        get("/setup/{doodleId?}") {
            // TODO: When admin removes dates, also remove corresponding participant answers
            val doodleId = call.getDoodleIdOrNull()
            if (doodleId == null) {  // No previous data
                call.respond(MustacheContent(TEMPLATE_NAME, mapOf("config" to DoodleConfig.SETUP)))
            } else {  // Show previous data
                if (databaseService.doodleIsClosed(doodleId)) {
                    call.respondRedirect("/view", false)
                } else {
                    val proposedDates = databaseService.getProposedDatesByDoodleId(doodleId).map { it.doodleDate }
                    val mustacheMapping = buildMustacheMapping(DoodleConfig.SETUP, defaultDates = proposedDates, doodleId = doodleId)
                    call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
                }
            }
        }

        /** Accepts setup dates for the Doodle */
        post("/setup/{doodleId?}") {
            val doodleId = call.getDoodleIdOrNull()
            if (doodleId == null) {  // Accept new data
                // TODO: At least one date must be selected
                val proposedDates = call.getDates()
                val doodle = databaseService.createDoodleFromDates(proposedDates)
                call.respond(HttpStatusCode.OK, doodle.id)
            } else {  // Update data
                val proposedDates = call.getDates()
                databaseService.updateDatesOfDoodle(doodleId, proposedDates)
                call.respond(HttpStatusCode.OK)
            }
        }

        /** Endpoint for answering and editing the answers of a Doodle */
        // Clean slate & show previous state
        // UUID is mandatory
        get("/answer/{doodleId?}") {
            // TODO: Authentication / recognize user -> For now only one user w/o authentication
            // TODO: This also needs to show already chosen dates (defaultDates = yesDates), i.e. editing must be possible (auth first and retrieve answers if any)
            // TODO: Get Participations (of all users) and show them in the calendar
            val doodleId = call.getDoodleId()
            val proposedDates = databaseService.getProposedDatesByDoodleId(doodleId).map { it.doodleDate }
            val mustacheMapping = buildMustacheMapping(DoodleConfig.ANSWER, enabledDates = proposedDates, doodleId = doodleId)
            call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
        }

        /** Accepts answer and their edits of a Doodle */
        // Accept new data & update data
        post("/answer/{doodleId?}") {
            // TODO: Check if proposedDates containsAll answeredDates
            // TODO: This also needs to accept edits on answers
            // TODO: Implement auth, to use real data
            val doodleId = call.getDoodleId()
            val yesDates = call.getDates()

            val newParticipant = NewParticipant(LocalTime.now().toString())
            val participant = databaseService.addParticipantIfNotExisting(newParticipant)

            // Add Participations
            // TODO: databaseService.updateParticipations(doodleId, participant, yesDates) needs auth
            databaseService.addParticipations(doodleId, participant, yesDates)

            call.respond(HttpStatusCode.OK)
        }

        /** Endpoint for closing a Doodle */
        get("/close/{doodleId?}") {
            // Needs: pickableDates, Participations
            val doodleId = call.getDoodleId()
            val proposedDates = databaseService.getProposedDatesByDoodleId(doodleId).map { it.doodleDate }
            val participations = databaseService.getParticipationsByDoodleId(doodleId)
            val numberOfParticipants = databaseService.getDoodleById(doodleId).numberOfParticipants
            val mustacheMapping = buildMustacheMapping(DoodleConfig.CLOSE,
                    enabledDates = proposedDates,
                    numberOfParticipants = numberOfParticipants,
                    participations = participations,
                    doodleId = doodleId)
            call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
        }

        /** Accepts final dates of a Doodle*/
        // Accept new data, no updates!
        post("/close/{doodleId?}") {
            val doodleId = call.getDoodleId()
            val finalDates = call.getDates()
            databaseService.markDatesAsFinal(doodleId, finalDates)
            databaseService.markDoodleAsClosed(doodleId)

            // Redirect admin to /view/{doodleId}
            //call.respondRedirect("/view/${doodleId}")
            call.respond(HttpStatusCode.OK)
        }

        /** Endpoint for viewing the results of a Doodle*/
        // Present (all) data
        get("/view/{doodleId?}") {
            // TODO: Show own chosen Dates? (auth first and retrieve yesDates if any)
            val doodleId = call.getDoodleId()
            val participations = databaseService.getParticipationsByDoodleId(doodleId)
            val numberOfParticipants = databaseService.getDoodleById(doodleId).numberOfParticipants
            val proposedDates = databaseService.getProposedDatesByDoodleId(doodleId).map { it.doodleDate }
            val finalDates = databaseService.getFinalDatesByDoodleId(doodleId).map { it.doodleDate }
            val mustacheMapping = buildMustacheMapping(DoodleConfig.VIEW,
                    enabledDates = proposedDates,
                    finalDates = finalDates,
                    numberOfParticipants = numberOfParticipants,
                    participations = participations,
                    doodleId = doodleId)
            call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
        }
    }
}