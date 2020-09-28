import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.application.*
import DoodleConfig
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.mustache.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun main() {
    embeddedServer(Netty, port = 8080, host = "127.0.0.1", module = Application::module, watchPaths = listOf("/DoodleBotWebBackend/")).start(wait = true)
}

fun Application.module() {
    install(Mustache) { mustacheFactory = DefaultMustacheFactory("templates") }
    install(ContentNegotiation) { gson {} }

    routing {
        get("/setup") {
            val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
            val formattedDate = LocalDate.now().format(formatter)
            val config = DoodleConfig(formattedDate)
            call.respond(MustacheContent("hello.hbs", mapOf("config" to config)))
        }

        post("/genDoodle") {
            val pickedDates: List<LocalDate> = call.receive()
            println(pickedDates)
            call.respond(HttpStatusCode.OK)
        }

    }
}