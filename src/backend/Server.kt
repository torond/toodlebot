package io.toodlebot.backend

import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.features.ContentTransformationException
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.mustache.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.toodlebot.backend.service.*
import io.toodlebot.bot.sendShareableToodle
import io.toodlebot.bot.sendViewButtonToChats
import io.toodlebot.bot.setup
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*
import kotlin.concurrent.thread
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import org.jetbrains.exposed.dao.id.EntityID

const val TEMPLATE_PATH = "templates"
const val TEMPLATE_NAME = "frontend.mustache"

@Suppress("unused")  // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    DatabaseFactory  // To trigger init block. Is there a better way to do this?
    val databaseService = DatabaseService()
    val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    val bot = setup(databaseService)
    thread(start = true) {
        bot.start()
    }

    // Delete expired Toodles once per day
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
            call.respond(HttpStatusCode.BadRequest) // If request is missing toodleId or dates or session could not be found
            log.warn(cause.stackTraceToString())
        }
        exception<NotFoundException> { cause ->
            call.respond(HttpStatusCode.NotFound)  // If Toodle or dates are not found
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

    /**
     * Retrieve the Toodle ID from the route parameters.
     * Throws [BadRequestException] if ID cannot be found.
     */
    fun ApplicationCall.getToodleId(): UUID {
        this.parameters["toodleId"] ?: throw BadRequestException("Must provide id")
        return UUID.fromString(this.parameters["toodleId"])
    }

    /**
     * Retrieve the Toodle ID from the route parameters.
     * Returns null if ID cannot be found.
     */
    fun ApplicationCall.getToodleIdOrNull(): UUID? {
        return this.parameters["toodleId"]?.let { UUID.fromString(it) }
    }

    /**
     * Retrieve list of dates from request body.
     * Throws [BadRequestException] if data cannot be found.
     */
    suspend fun ApplicationCall.getDates(): List<LocalDate> {
        val raw = this.receiveOrNull<List<String>>() ?: throw BadRequestException("Must provide dates")
        return raw.map { LocalDate.parse(it, inputFormatter) }
    }

    /**
     * Retrieve list of dates and title from request body.
     * Throws [BadRequestException] if data cannot be found.
     */
    suspend fun ApplicationCall.getDatesAndTitle(): JsonData =
        this.receiveOrNull() ?: throw BadRequestException("Must provide dates")

    /**
     * Retrieve and verify login data supplied by Telegram.
     * auth_date, id, and hash are not-null.
     */
    fun ApplicationCall.getAndVerifyTelegramLoginData(): LoginData {
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

    /**
     * Look up the client session.
     */
    fun ApplicationCall.getLoginSession(): LoginSession {
        // TODO: Better logging
        val loginSession = this.sessions.get<LoginSession>() ?: throw BadRequestException("Session error.")
        this.application.environment.log.debug("${this.request.httpMethod.value} to ${this.request.path()} by $loginSession")
        return loginSession
    }

    /**
     * Set a new client session.
     */
    fun ApplicationCall.setLoginSession(loginSession: LoginSession) {
        // TODO: Better logging
        this.sessions.set(loginSession)
        this.application.environment.log.debug("${this.request.httpMethod.value} to ${this.request.path()} by $loginSession")
    }

    /**
     * Helper function for building the mapping used in frontend.mustache.
     */
    fun buildMustacheMapping(
            config: ToodleConfig,
            title: String? = null,
            toodleId: UUID? = null,
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
        if (toodleId != null) mappings.add("toodleId" to toodleId)
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

        /** Redirect to /view if a Toodle is closed. */
        intercept(ApplicationCallPipeline.Call) {
            this.call.getToodleIdOrNull()?.let {
                if (!this.call.request.uri.startsWith("/view") && databaseService.toodleIsClosed(it)) {
                    this.call.respondRedirect("/view/$it?${this.call.request.queryString()}")
                    this.finish()
                }
            }
        }

        /** Endpoint for setting up and editing the initial dates of a Toodle */
        get("/setup/{toodleId?}") {
            val toodleId = call.getToodleIdOrNull()
            val loginData = call.getAndVerifyTelegramLoginData()
            call.setLoginSession(LoginSession(loginData.userId))

            if (toodleId == null) {  // No previous data, create Toodle
                call.respond(MustacheContent(TEMPLATE_NAME, mapOf("config" to ToodleConfig.SETUP, "botUsername" to Env.botUsername)))
            } else {  // Show previous data, edit Toodle
                databaseService.assertIsAdmin(toodleId, loginData.userId)
                val proposedDates = databaseService.getProposedDatesByToodleId(toodleId)
                val toodle = databaseService.getToodleById(toodleId)
                val mustacheMapping = buildMustacheMapping(
                    ToodleConfig.SETUP,
                    defaultDates = proposedDates,
                    toodleId = toodleId,
                    title = toodle.title
                )
                call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
            }
        }

        /** Accepts setup dates for the Toodle */
        post("/setup/{toodleId?}") {
            val toodleId = call.getToodleIdOrNull()
            val loginSession = call.getLoginSession()

            if (toodleId == null) {  // Accept new data
                // TODO: At least one date must be selected
                val jsonData = call.getDatesAndTitle()
                val re = Regex("[^A-Za-z0-9 .-]")
                val title = re.replace(jsonData.title, "")
                val toodle = databaseService.createToodleFromDates(
                    jsonData.dates.map { LocalDate.parse(it, DateUtil.inputFormatter) },
                    title,
                    loginSession.userId
                )
                // Send reply to user
                bot.sendShareableToodle(loginSession.userId, toodle)
                call.respond(HttpStatusCode.OK, toodle.id)
            } else {  // Update data
                databaseService.assertIsAdmin(toodleId, loginSession.userId)
                val jsonData = call.getDatesAndTitle()
                databaseService.updateDatesOfToodle(
                    toodleId,
                    jsonData.dates.map { LocalDate.parse(it, DateUtil.inputFormatter) })
                databaseService.updateTitleOfToodle(toodleId, jsonData.title)
                databaseService.refreshExpirationDate(toodleId)
                call.respond(HttpStatusCode.OK)
            }
        }

        /** Endpoint for answering and editing the answers of a Toodle */
        get("/answer/{toodleId?}") {
            val toodleId = call.getToodleId()
            val loginData = call.getAndVerifyTelegramLoginData()
            call.setLoginSession(LoginSession(loginData.userId))

            val toodle = databaseService.getToodleById(toodleId)
            val proposedDates = databaseService.getProposedDatesByToodleId(toodleId)
            val yesDates = databaseService.getYesDatesByToodleIdAndParticipantUserId(
                toodleId,
                loginData.userId
            )
            val mustacheMapping = buildMustacheMapping(
                ToodleConfig.ANSWER,
                enabledDates = proposedDates,
                defaultDates = yesDates,
                toodleId = toodleId,
                title = toodle.title
            )
            call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
        }

        /** Accepts answer and their edits of a Toodle */
        post("/answer/{toodleId?}") {
            val toodleId = call.getToodleId()
            val proposedDates = databaseService.getProposedDatesByToodleId(toodleId)
            val yesDates = call.getDates().intersect(proposedDates).toList()
            val loginSession = call.getLoginSession()


            val participantId = if (databaseService.notYetParticipating(toodleId, loginSession.userId)) {
                databaseService.addParticipantToToodle(toodleId, loginSession.userId)
            } else {
                databaseService.getParticipantId(toodleId, loginSession.userId) ?: throw NotFoundException("Participant with userId ${loginSession.userId} not found")
            }

            // Update participations
            databaseService.updateParticipations(toodleId, participantId, yesDates)
            databaseService.refreshExpirationDate(toodleId)
            call.respond(HttpStatusCode.OK)
        }

        /** Endpoint for closing a Toodle */
        get("/close/{toodleId?}") {
            val toodleId = call.getToodleId()
            val loginData = call.getAndVerifyTelegramLoginData()
            call.setLoginSession(LoginSession(loginData.userId))

            databaseService.assertIsAdmin(toodleId, loginData.userId)
            val toodle = databaseService.getToodleById(toodleId)
            val proposedDates = databaseService.getProposedDatesByToodleId(toodleId)
            val participations = databaseService.getParticipationMap(toodleId)
            val mustacheMapping = buildMustacheMapping(
                ToodleConfig.CLOSE,
                enabledDates = proposedDates,
                numberOfParticipants = toodle.numberOfParticipants,
                participations = participations,
                toodleId = toodleId,
                title = toodle.title
            )
            call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
        }

        /** Accepts final dates of a Toodle*/
        post("/close/{toodleId?}") {
            val toodleId = call.getToodleId()
            val loginSession = call.getLoginSession()

            databaseService.assertIsAdmin(toodleId, loginSession.userId)

            val finalDates = call.getDates()
            databaseService.markDatesAsFinal(toodleId, finalDates)
            databaseService.markToodleAsClosed(toodleId)
            databaseService.refreshExpirationDate(toodleId)

            val sharedGroupIds = databaseService.getChatIdsOfToodle(toodleId)
            bot.sendViewButtonToChats(sharedGroupIds, toodleId.toString())
            call.respond(HttpStatusCode.OK)
        }

        /** Endpoint for viewing the results of a Toodle*/
        get("/view/{toodleId?}") {
            val toodleId = call.getToodleId()
            val loginData = call.getAndVerifyTelegramLoginData()

            val toodle = databaseService.getToodleById(toodleId)
            val participations = databaseService.getParticipationMap(toodleId)
            val proposedDates = databaseService.getProposedDatesByToodleId(toodleId)
            val finalDates = databaseService.getFinalDatesByToodleId(toodleId)
            val mustacheMapping = buildMustacheMapping(
                ToodleConfig.VIEW,
                enabledDates = proposedDates,
                finalDates = finalDates,
                numberOfParticipants = toodle.numberOfParticipants,
                participations = participations,
                toodleId = toodleId,
                title = toodle.title
            )
            call.respond(MustacheContent(TEMPLATE_NAME, mustacheMapping))
        }

        /** Endpoint for closing a Toodle */
        get("/delete/{toodleId?}") {
            // TODO: Should not use GET
            val toodleId = call.getToodleId()
            val loginData = call.getAndVerifyTelegramLoginData()

            databaseService.assertIsAdmin(toodleId, loginData.userId)
            databaseService.deleteToodle(toodleId)
            call.respond(MustacheContent("deleted.mustache", null))
        }


    }
}