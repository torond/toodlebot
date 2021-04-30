package io.doodlebot.backend

import com.github.mustachejava.DefaultMustacheFactory
import io.doodlebot.backend.service.*
import io.doodlebot.bot.sendShareableDoodle
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
import io.ktor.http.content.*
import io.ktor.sessions.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import java.time.Duration
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

    // Delete expired Doodles once per day
    // TODO: Better way of scheduling (with start time)
    GlobalScope.launch {
        while (true) {
            delay(Duration.ofDays(1))
            databaseService.deleteExpiredToodles()
            println("Delete expired")
        }
    }

    install(Mustache) { mustacheFactory = DefaultMustacheFactory(TEMPLATE_PATH) }
    install(ContentNegotiation) { gson {} }
    install(StatusPages) {
        statusFile(HttpStatusCode.BadRequest,
            HttpStatusCode.Unauthorized,
            HttpStatusCode.NotFound,
            HttpStatusCode.Forbidden,
            filePattern = "templates/error#.html")
        exception<BadRequestException> { cause ->
            call.respond(HttpStatusCode.BadRequest) // If request is missing doodleId or dates or session could not be found
            log.warn(cause.stackTraceToString())
        }
        exception<NotFoundException> { cause ->
            call.respond(HttpStatusCode.NotFound)  // If Doodle or dates are not found
            log.warn(cause.stackTraceToString())
        }
        exception<DateTimeParseException> { cause ->
            call.respond(HttpStatusCode.BadRequest)
            log.warn(cause.stackTraceToString())
        }
        exception<ContentTransformationException> { cause ->
            call.respond(HttpStatusCode.BadRequest)  // If content cannot be negotiated
            log.warn(cause.stackTraceToString())
        }
        exception<IllegalArgumentException> { cause ->
            call.respond(HttpStatusCode.BadRequest)  // If content cannot be negotiated
            log.warn(cause.stackTraceToString())
        }
        exception<IllegalStateException> { cause ->
            call.respond(HttpStatusCode.Forbidden)
            log.warn(cause.stackTraceToString())
        }
        exception<AssertionError> { cause ->
            call.respond(HttpStatusCode.Unauthorized)  // If Telegram data could not be verified
            log.warn(cause.stackTraceToString())
        }
        exception<DateTimeParseException> { cause ->
            call.respond(HttpStatusCode.BadRequest)  // If received dates are malformed
            log.warn(cause.stackTraceToString())
        }
    }

    install(Sessions) { cookie<LoginSession>("LOGIN_SESSION", storage = SessionStorageMemory()) }

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
        // We know that auth_date, id, and hash will be supplied by Telegram
        // If they are missing, something is wrong.
        val authDate = checkNotNull(this.request.queryParameters["auth_date"])
        val id = checkNotNull(this.request.queryParameters["id"])
        val hash = checkNotNull(this.request.queryParameters["hash"])

        return LoginData(
            authDate,
            this.request.queryParameters["first_name"],
            id,
            this.request.queryParameters["last_name"],
            this.request.queryParameters["photo_url"],
            this.request.queryParameters["username"],
            hash
        )
    }

    fun ApplicationCall.getLoginSession(): LoginSession {
        // TODO: Better logging
        val loginSession = this.sessions.get<LoginSession>() ?: throw BadRequestException("Session error.")
        this.application.environment.log.debug("${this.request.httpMethod.value} to ${this.request.path()} by $loginSession")
        return loginSession
    }

    fun ApplicationCall.setLoginSession(loginSession: LoginSession) {
        // TODO: Better logging
        this.sessions.set(loginSession)
        this.application.environment.log.debug("${this.request.httpMethod.value} to ${this.request.path()} by $loginSession")
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
        mappings.add("botUsername" to Env.botUsername)
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
        static("/assets") {
            resources("templates")
        }
        intercept(ApplicationCallPipeline.Call) {
            this.call.getDoodleIdOrNull()?.let {
                if (!this.call.request.uri.startsWith("/view") && databaseService.toodleIsClosed(it)) {
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
            call.setLoginSession(LoginSession(loginData.userId))

            if (doodleId == null) {  // No previous data, create Doodle
                call.respond(MustacheContent(TEMPLATE_NAME, mapOf("config" to DoodleConfig.SETUP, "botUsername" to Env.botUsername)))
            } else {  // Show previous data, edit Doodle
                databaseService.assertIsAdmin(doodleId, loginData.userId)
                val proposedDates = databaseService.getProposedDatesByToodleId(doodleId)
                val doodleInfo = databaseService.getToodleById(doodleId)
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
            val loginSession = call.getLoginSession()

            if (doodleId == null) {  // Accept new data
                // TODO: At least one date must be selected
                val jsonData = call.getDatesAndTitle()
                val re = Regex("[^A-Za-z0-9 .-]")
                val title = re.replace(jsonData.title, "")
                val doodle = databaseService.createToodleFromDates(
                    jsonData.dates.map { LocalDate.parse(it, DateUtil.inputFormatter) },
                    title,
                    loginSession.userId
                )
                // Send reply to user
                bot.sendShareableDoodle(loginSession.userId, doodle)
                call.respond(HttpStatusCode.OK, doodle.id)
            } else {  // Update data
                databaseService.assertIsAdmin(doodleId, loginSession.userId)
                val jsonData = call.getDatesAndTitle()
                databaseService.updateDatesOfToodle(
                    doodleId,
                    jsonData.dates.map { LocalDate.parse(it, DateUtil.inputFormatter) })
                databaseService.updateTitleOfToodle(doodleId, jsonData.title)
                databaseService.refreshExpirationDate(doodleId)
                call.respond(HttpStatusCode.OK)
            }
        }

        /** Endpoint for answering and editing the answers of a Doodle */
        // Clean slate & show previous state
        // UUID is mandatory
        get("/answer/{doodleId?}") {
            val doodleId = call.getDoodleId()
            val loginData = call.getAndVerifyTelegramLoginData()
            call.setLoginSession(LoginSession(loginData.userId))

            val doodleInfo = databaseService.getToodleById(doodleId)
            val proposedDates = databaseService.getProposedDatesByToodleId(doodleId)
            val yesDates = databaseService.getYesDatesByToodleIdAndParticipantUserId(
                doodleId,
                loginData.userId
            )  // If there are dates associated to this userId and doodleId, returns them. Else empty list.
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
            val proposedDates = databaseService.getProposedDatesByToodleId(doodleId)
            val yesDates = call.getDates().intersect(proposedDates).toList()
            val loginSession = call.getLoginSession()

            // Update or add participations
            if (databaseService.notYetParticipating(doodleId, loginSession.userId)) {
                val participantId = databaseService.addParticipantToToodle(doodleId, loginSession.userId)
                // replace following with updateParticipations
                databaseService.updateParticipations(doodleId, participantId, yesDates)
                databaseService.refreshExpirationDate(doodleId)
                call.respond(HttpStatusCode.OK)
            } else {
                val participantId = databaseService.getParticipantId(doodleId, loginSession.userId) ?: throw NotFoundException("Participant with userId ${loginSession.userId} not found")
                databaseService.updateParticipations(doodleId, participantId, yesDates)
                databaseService.refreshExpirationDate(doodleId)
                call.respond(HttpStatusCode.OK)
            }


        }

        /** Endpoint for closing a Doodle */
        get("/close/{doodleId?}") {
            val doodleId = call.getDoodleId()
            val loginData = call.getAndVerifyTelegramLoginData()
            call.setLoginSession(LoginSession(loginData.userId))

            databaseService.assertIsAdmin(doodleId, loginData.userId)
            val doodleInfo = databaseService.getToodleById(doodleId)
            val proposedDates = databaseService.getProposedDatesByToodleId(doodleId)
            val participations = databaseService.getParticipationMap(doodleId)
            val numberOfParticipants = databaseService.getToodleById(doodleId).numberOfParticipants
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
            val loginSession = call.getLoginSession()

            databaseService.assertIsAdmin(doodleId, loginSession.userId)
            val finalDates = call.getDates()
            databaseService.markDatesAsFinal(doodleId, finalDates)
            databaseService.markToodleAsClosed(doodleId)
            val sharedGroupIds = databaseService.getChatIdsOfToodle(doodleId)

            bot.sendViewButtonToChats(sharedGroupIds, doodleId.toString())
            databaseService.refreshExpirationDate(doodleId)
            call.respond(HttpStatusCode.OK)
        }

        /** Endpoint for viewing the results of a Doodle*/
        // Present (all) data
        get("/view/{doodleId?}") {
            // TODO: Show own chosen Dates? (auth first and retrieve yesDates if any)
            val doodleId = call.getDoodleId()
            val loginData = call.getAndVerifyTelegramLoginData()
            call.setLoginSession(LoginSession(loginData.userId))

            val doodleInfo = databaseService.getToodleById(doodleId)
            val participations = databaseService.getParticipationMap(doodleId)
            val proposedDates = databaseService.getProposedDatesByToodleId(doodleId)
            val finalDates = databaseService.getFinalDatesByToodleId(doodleId)
            val mustacheMapping = buildMustacheMapping(
                DoodleConfig.VIEW,
                enabledDates = proposedDates,
                finalDates = finalDates,
                numberOfParticipants = doodleInfo.numberOfParticipants,
                participations = participations,
                doodleId = doodleId,
                title = doodleInfo.title
            )
            call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
        }

        /** Endpoint for closing a Doodle */
        get("/delete/{doodleId?}") {
            // TODO: Should not use GET
            val doodleId = call.getDoodleId()
            val loginData = call.getAndVerifyTelegramLoginData()

            databaseService.assertIsAdmin(doodleId, loginData.userId)
            databaseService.deleteToodle(doodleId)
            call.respond(MustacheContent("deleted.mustache", null))
        }


    }
}