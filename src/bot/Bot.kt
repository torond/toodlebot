package io.doodlebot.bot

import com.elbekD.bot.Bot
import com.elbekD.bot.types.InlineKeyboardButton
import com.elbekD.bot.types.InlineKeyboardMarkup
import com.elbekD.bot.types.LoginUrl
import com.elbekD.bot.util.keyboard.KeyboardFactory
import io.doodlebot.backend.service.DatabaseService
import io.doodlebot.backend.service.Env
import java.util.*

fun setup(databaseService: DatabaseService): Bot {

    val bot = Bot.createPolling(Env.botUsername, Env.botToken)
    println("Server URL for setup: ${Env.host}:${Env.port}/setup")

    bot.onCommand("/start") { msg, value ->
        if (value == null) {  // Someone creates a Doodle
            bot.sendMessage(
                msg.chat.id,
                "Click the button below to create a new Doodle!",
                markup = InlineKeyboardMarkup(
                    KeyboardFactory.inlineMarkup(
                        listOf(
                            InlineKeyboardButton(
                                "Create Doodle",
                                login_url = LoginUrl("${Env.host}:${Env.port}/setup", request_write_access = true)
                            )
                        )
                    )
                )
            )
        } else {  // Bot was added to a group chat
            // TODO: Check that input is not malicious, anyone can enter "/start somethingbad"
            val doodleId = UUID.fromString(value)
            val chatIds = databaseService.getChatIdsOfToodle(doodleId)
            if(!chatIds.contains(msg.chat.id)) {
                databaseService.addChatIdToToodle(doodleId, msg.chat.id)
                bot.sendMessage(
                    msg.chat.id,  // ID of the chosen group chat
                    "Answer the Doodle with the button below. You can also edit your answer.",
                    markup = InlineKeyboardMarkup(
                        KeyboardFactory.inlineMarkup(
                            listOf(
                                InlineKeyboardButton(
                                    "Answer Doodle / Edit Answer",
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

fun Bot.sendShareableDoodle(chatId: String, doodleId: String, title: String) {
    this.sendMessage(
        chatId,
        "Doodle \"$title\" created. Use the buttons share the Doodle to a group or close it.",
        markup = InlineKeyboardMarkup(
            KeyboardFactory.inlineMarkup(
                listOf(
                    InlineKeyboardButton(
                        "Share Doodle",
                        url = "https://t.me/${Env.botUsername}?startgroup=$doodleId"
                    ),
                    InlineKeyboardButton(
                        "Edit Doodle",
                        login_url = LoginUrl(
                            "${Env.host}:${Env.port}/setup/$doodleId"
                        )
                    ),
                    InlineKeyboardButton(
                        "Close Doodle",
                        login_url = LoginUrl(
                            "${Env.host}:${Env.port}/close/$doodleId"
                        )
                    )
                )
            )
        )
    )
}

fun Bot.sendViewButton(chatId: String, doodleId: String) {
    this.sendMessage(
        chatId,
        "The Doodle was closed. Use the button to view the results.",
        markup = InlineKeyboardMarkup(
            KeyboardFactory.inlineMarkup(
                listOf(
                    InlineKeyboardButton(
                        "View Doodle",
                        login_url = LoginUrl(
                            "${Env.host}:${Env.port}/view/$doodleId"
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
fun Bot.sendViewButtonToChats(chatIds: List<Long>, doodleId: String) {
    for (chatId in chatIds) {
        this.sendMessage(
            chatId,
            "The Doodle was closed. Use the button to view the results.",
            markup = InlineKeyboardMarkup(
                KeyboardFactory.inlineMarkup(
                    listOf(
                        InlineKeyboardButton(
                            "View Doodle",
                            login_url = LoginUrl(
                                "${Env.host}:${Env.port}/view/$doodleId"
                            )
                        )
                    )
                )
            )
        )
    }
}