package io.toodlebot.bot

import com.elbekD.bot.Bot
import com.elbekD.bot.types.InlineKeyboardButton
import com.elbekD.bot.types.InlineKeyboardMarkup
import com.elbekD.bot.types.LoginUrl
import com.elbekD.bot.util.keyboard.KeyboardFactory
import io.toodlebot.backend.model.Toodle
import io.toodlebot.backend.service.DatabaseService
import io.toodlebot.backend.service.Env
import java.util.*

fun setup(databaseService: DatabaseService): Bot {

    val bot = Bot.createPolling(Env.botUsername, Env.botToken)
    println("Server URL for setup: ${Env.host}:${Env.port}/setup")

    bot.onCommand("/start") { msg, value ->
        if (value == null) {  // Someone creates a Toodle
            println(msg.chat.id)
            bot.sendMessage(
                msg.chat.id,
                "Click the button below to create a new Toodle!",
                markup = InlineKeyboardMarkup(
                    KeyboardFactory.inlineMarkup(
                        listOf(
                            InlineKeyboardButton(
                                "Create Toodle",
                                login_url = LoginUrl("${Env.host}:${Env.port}/setup", request_write_access = true)
                            )
                        )
                    )
                )
            )
        } else {  // Bot was added to a group chat
            // TODO: Check that input is not malicious, anyone can enter "/start somethingbad"
            val toodleId = UUID.fromString(value)
            val chatIds = databaseService.getChatIdsOfToodle(toodleId)
            println(msg.chat.id)
            if(!chatIds.contains(msg.chat.id)) {
                databaseService.addChatIdToToodle(toodleId, msg.chat.id)
                bot.sendMessage(
                    msg.chat.id,  // ID of the chosen group chat
                    "Answer the Toodle with the button below. You can also edit your answer.",
                    markup = InlineKeyboardMarkup(
                        KeyboardFactory.inlineMarkup(
                            listOf(
                                InlineKeyboardButton(
                                    "Answer Toodle / Edit Answer",
                                    login_url = LoginUrl(
                                        "${Env.host}:${Env.port}/answer/$value"
                                    )
                                )
                            )
                        )
                    )
                )
            }
        }
    }

    return bot
}

fun Bot.sendShareableToodle(chatId: String, toodle: Toodle) {
    this.sendMessage(
        chatId,
        "Toodle \"${toodle.title}\" created. Use the buttons share the it to a group, close, edit or delete it. It will automatically expire if there is no activity for one week.",
        markup = InlineKeyboardMarkup(
            KeyboardFactory.inlineMarkup(
                listOf(
                    InlineKeyboardButton(
                        "Share Toodle",
                        url = "https://t.me/${Env.botUsername}?startgroup=${toodle.id}"
                    ),
                    InlineKeyboardButton(
                        "Edit Toodle",
                        login_url = LoginUrl(
                            "${Env.host}:${Env.port}/setup/${toodle.id}"
                        )
                    ),
                    InlineKeyboardButton(
                        "Close Toodle",
                        login_url = LoginUrl(
                            "${Env.host}:${Env.port}/close/${toodle.id}"
                        )
                    ),
                    InlineKeyboardButton(
                            "Delete Toodle",
                            login_url = LoginUrl(
                                    "${Env.host}:${Env.port}/delete/${toodle.id}"
                            )
                    )
                )
            )
        )
    )
}

/**
 * Sends buttons /view/<UUID> to the given chatIds
 */
fun Bot.sendViewButtonToChats(chatIds: List<Long>, toodleId: String) {
    for (chatId in chatIds) {
        this.sendMessage(
            chatId,
            "The Toodle was closed. Use the button to view the results. It'll be deleted automatically in a week.",
            markup = InlineKeyboardMarkup(
                KeyboardFactory.inlineMarkup(
                    listOf(
                        InlineKeyboardButton(
                            "View Toodle",
                            login_url = LoginUrl(
                                "${Env.host}:${Env.port}/view/$toodleId"
                            )
                        )
                    )
                )
            )
        )
    }
}