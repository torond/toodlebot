import com.elbekD.bot.Bot
import com.elbekD.bot.feature.chain.chain
import com.elbekD.bot.feature.chain.jumpTo
import com.elbekD.bot.feature.chain.jumpToAndFire

/**
 * Hallo, Welt!
 */
fun main() {
    val token = "1264466484:AAE1TzsjmxfGCGcrq8Ny8Is7enqXMnTYm3U"
    val username = "KotlinOBottleBot"
    val bot = Bot.createPolling(username, token)

    bot.chain("/start") { msg -> bot.sendMessage(msg.chat.id, "Hi! What is your name?") }
        .then { msg -> bot.sendMessage(msg.chat.id, "Nice to meet you, ${msg.text}! Send something to me") }
        .then { msg -> bot.sendMessage(msg.chat.id, "Fine! See you soon") }
        .build()

    bot.chain(
        label = "location_chain",
        predicate = { msg -> msg.location != null },
        action = { msg ->
            bot.sendMessage(
                msg.chat.id,
                "Fine, u've sent me a location. Is this where you want to order a taxi?(yes|no)"
            )
        })
        .then("answer_choice") { msg ->
            when (msg.text) {
                "yes" -> bot.jumpToAndFire("order_taxi", msg)
                "no" -> bot.jumpToAndFire("cancel_ordering", msg)
                else -> {
                    bot.sendMessage(msg.chat.id, "Oops, I don't understand you. Just answer yes or no?")
                    bot.jumpTo("answer_choice", msg)
                }
            }
        }
        .then("order_taxi", isTerminal = true) { msg ->
            bot.sendMessage(msg.chat.id, "Fine! Taxi is coming")
        }
        .then("cancel_ordering", isTerminal = true) { msg ->
            bot.sendMessage(msg.chat.id, "Ok! See you next time")
        }
        .build()

    bot.chain(
        label = "sticker chain",
        predicate = { msg -> msg.sticker != null },
        action = { msg ->
            bot.sendMessage(msg.chat.id, "Sticker!")
        })
        .build()

    bot.start()
}