package io.doodlebot.backend

import com.github.mustachejava.DefaultMustacheFactory
import io.doodlebot.backend.model.NewParticipant
import io.doodlebot.backend.service.*
import io.doodlebot.bot.sendShareableDoodle
import io.doodlebot.bot.sendViewButton
import io.doodlebot.bot.sendViewButtonToChats
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
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*
import io.doodlebot.bot.setup
import io.ktor.sessions.*
import kotlin.concurrent.thread

const val TEMPLATE_PATH = "templates"
const val TEMPLATE_NAME = "frontend.mustache"

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    DatabaseFactory  // To trigger init block. Is there a better way to do this?
    val databaseService = DatabaseService()
    val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    val bot = setup(databaseService)
    thread(start = true) {
        bot.start()
    }
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
        exception<AssertionError> { cause ->
            call.respond(HttpStatusCode.Forbidden)  // If Telegram data could not be verified
            log.warn(cause.message)
        }
        // UnauthorizedException if /setup/{doodleId} is queried by non-admin
    }
    install(Sessions) { cookie<LoginData>("LOGIN_SESSION", storage = SessionStorageMemory()) }

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

    suspend fun ApplicationCall.getDatesAndTitle(): JsonData =
        this.receiveOrNull() ?: throw BadRequestException("Must provide dates")

    fun ApplicationCall.getAndVerifyTelegramLoginData(): LoginData {
        // We know that auth_date, id, username and hash will be supplied by Telegram
        // If they are missing, something is wrong.
        val authDate = checkNotNull(this.request.queryParameters["auth_date"])
        val id = checkNotNull(this.request.queryParameters["id"])
        val username = checkNotNull(this.request.queryParameters["username"])
        val hash = checkNotNull(this.request.queryParameters["hash"])

        return LoginData(
            authDate,
            this.request.queryParameters["first_name"],
            id,
            this.request.queryParameters["last_name"],
            this.request.queryParameters["photo_url"],
            username,
            hash
        )
    }

    fun ApplicationCall.getLoginSession(): LoginData {
        // TODO: Better logging
        val loginData = this.sessions.get<LoginData>() ?: throw BadRequestException("No session data provided")
        this.application.environment.log.debug("${this.request.httpMethod.value} to ${this.request.path()} by $loginData")
        return loginData
    }

    fun ApplicationCall.setLoginSession(loginData: LoginData) {
        // TODO: Better logging
        this.sessions.set(loginData)
        this.application.environment.log.debug("${this.request.httpMethod.value} to ${this.request.path()} by $loginData")
    }

    fun buildMustacheMapping(
        config: DoodleConfig,
        title: String? = null,
        doodleId: UUID? = null,
        enabledDates: List<LocalDate>? = null,
        defaultDates: List<LocalDate>? = null,
        finalDates: List<LocalDate>? = null,
        numberOfParticipants: Int? = null,
        participations: Map<LocalDate, List<EntityID<Int>>>? = null
    ): Map<String, Any> {
        val mappings: MutableList<Pair<String, Any>> = mutableListOf()
        mappings.add("config" to config)
        if (title != null) mappings.add("title" to title)
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
                if (!this.call.request.uri.startsWith("/view") && databaseService.doodleIsClosed(it)) {
                    this.call.respondRedirect("/view/$it?${this.call.request.queryString()}")
                    this.finish()
                }
            }
        }

        /** Endpoint for setting up and editing the initial dates of a Doodle */
        get("/setup/{doodleId?}") {
            // TODO: Check for existing session and if user is authorized to access this (is admin)
            // -> Only if doodleId is given, otherwise it counts as a new Doodle
            val doodleId = call.getDoodleIdOrNull()
            val loginData = call.getAndVerifyTelegramLoginData()
            call.setLoginSession(loginData)

            if (doodleId == null) {  // No previous data, create Doodle
                call.respond(MustacheContent(TEMPLATE_NAME, mapOf("config" to DoodleConfig.SETUP)))
            } else {  // Show previous data, edit Doodle
                databaseService.assertIsAdmin(doodleId, loginData.username)
                val proposedDates = databaseService.getProposedDatesByDoodleId(doodleId).map { it.doodleDate }
                val doodleInfo = databaseService.getDoodleById(doodleId)
                val mustacheMapping = buildMustacheMapping(
                    DoodleConfig.SETUP,
                    defaultDates = proposedDates,
                    doodleId = doodleId,
                    title = doodleInfo.title
                )
                call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
            }
        }

        /** Accepts setup dates for the Doodle */
        post("/setup/{doodleId?}") {
            // Get URL parameter
            val doodleId = call.getDoodleIdOrNull()
            val loginData = call.getLoginSession()

            if (doodleId == null) {  // Accept new data
                // TODO: At least one date must be selected
                val jsonData = call.getDatesAndTitle()
                val doodle = databaseService.createDoodleFromDates(
                    jsonData.dates.map { LocalDate.parse(it, DateUtil.inputFormatter) },
                    jsonData.title,
                    loginData.username
                )
                // Send reply to user
                bot.sendShareableDoodle(loginData.id, doodle.id.toString(), jsonData.title)
                call.respond(HttpStatusCode.OK, doodle.id)
            } else {  // Update data
                databaseService.assertIsAdmin(doodleId, loginData.username)
                val jsonData = call.getDatesAndTitle()
                databaseService.updateDatesOfDoodle(
                    doodleId,
                    jsonData.dates.map { LocalDate.parse(it, DateUtil.inputFormatter) })
                databaseService.updateTitleOfDoodle(doodleId, jsonData.title)
                call.respond(HttpStatusCode.OK)
            }
        }

        /** Endpoint for answering and editing the answers of a Doodle */
        // Clean slate & show previous state
        // UUID is mandatory
        get("/answer/{doodleId?}") {
            val doodleId = call.getDoodleId()
            val loginData = call.getAndVerifyTelegramLoginData()
            call.setLoginSession(loginData)

            val newParticipant = NewParticipant(loginData.username)
            databaseService.addParticipantIfNotExisting(newParticipant)

            val doodleInfo = databaseService.getDoodleById(doodleId)
            val proposedDates = databaseService.getProposedDatesByDoodleId(doodleId).map { it.doodleDate }
            val yesDates = databaseService.getYesDatesByDoodleIdAndParticipantUsername(
                doodleId,
                loginData.username
            )  // If there are dates associated to this username and doodleId, returns them. Else empty list.
            val mustacheMapping = buildMustacheMapping(
                DoodleConfig.ANSWER,
                enabledDates = proposedDates,
                defaultDates = yesDates,
                doodleId = doodleId,
                title = doodleInfo.title
            )
            call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
        }

        /** Accepts answer and their edits of a Doodle */
        // Accept new data & update data
        post("/answer/{doodleId?}") {
            val doodleId = call.getDoodleId()
            val proposedDates = databaseService.getProposedDatesByDoodleId(doodleId).map { it.doodleDate }
            val yesDates = call.getDates().intersect(proposedDates).toList()
            val loginData = call.getLoginSession()

            // Update or add participations
            val participant = databaseService.getParticipantByUsername(loginData.username)
            if (databaseService.hasNotAnswered(doodleId, participant)) {
                databaseService.addParticipations(doodleId, participant, yesDates)
                call.respond(HttpStatusCode.OK)
            } else {
                databaseService.updateParticipations(doodleId, participant, yesDates)
                call.respond(HttpStatusCode.OK)
            }


        }

        /** Endpoint for closing a Doodle */
        get("/close/{doodleId?}") {
            // Needs: pickableDates, Participations
            val doodleId = call.getDoodleId()
            val loginData = call.getAndVerifyTelegramLoginData()
            call.setLoginSession(loginData)

            databaseService.assertIsAdmin(doodleId, loginData.username)
            val doodleInfo = databaseService.getDoodleById(doodleId)
            val proposedDates = databaseService.getProposedDatesByDoodleId(doodleId).map { it.doodleDate }
            val participations = databaseService.getParticipationsByDoodleId(doodleId)
            val numberOfParticipants = databaseService.getDoodleById(doodleId).numberOfParticipants
            val mustacheMapping = buildMustacheMapping(
                DoodleConfig.CLOSE,
                enabledDates = proposedDates,
                numberOfParticipants = numberOfParticipants,
                participations = participations,
                doodleId = doodleId,
                title = doodleInfo.title
            )
            call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
        }

        /** Accepts final dates of a Doodle*/
        // Accept new data, no updates!
        post("/close/{doodleId?}") {
            val doodleId = call.getDoodleId()
            val loginData = call.getLoginSession()

            databaseService.assertIsAdmin(doodleId, loginData.username)
            val finalDates = call.getDates()
            databaseService.markDatesAsFinal(doodleId, finalDates)
            databaseService.markDoodleAsClosed(doodleId)
            val sharedGroupIds = databaseService.getChatIdsOfDoodle(doodleId)

            bot.sendViewButtonToChats(sharedGroupIds, doodleId.toString())
            call.respond(HttpStatusCode.OK)
        }

        /** Endpoint for viewing the results of a Doodle*/
        // Present (all) data
        get("/view/{doodleId?}") {
            // TODO: Show own chosen Dates? (auth first and retrieve yesDates if any)
            val doodleId = call.getDoodleId()
            val loginData = call.getAndVerifyTelegramLoginData()
            call.setLoginSession(loginData)

            val doodleInfo = databaseService.getDoodleById(doodleId)
            val participations = databaseService.getParticipationsByDoodleId(doodleId)
            val numberOfParticipants = databaseService.getDoodleById(doodleId).numberOfParticipants
            val proposedDates = databaseService.getProposedDatesByDoodleId(doodleId).map { it.doodleDate }
            val finalDates = databaseService.getFinalDatesByDoodleId(doodleId).map { it.doodleDate }
            val mustacheMapping = buildMustacheMapping(
                DoodleConfig.VIEW,
                enabledDates = proposedDates,
                finalDates = finalDates,
                numberOfParticipants = numberOfParticipants,
                participations = participations,
                doodleId = doodleId,
                title = doodleInfo.title
            )
            call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
        }
    }
}