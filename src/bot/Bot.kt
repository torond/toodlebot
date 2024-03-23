package io.toodlebot.bot

import com.elbekd.bot.Bot
import com.elbekd.bot.model.toChatId
import com.elbekd.bot.types.InlineKeyboardButton
import com.elbekd.bot.types.InlineKeyboardMarkup
import com.elbekd.bot.types.LoginUrl
import com.elbekd.bot.util.keyboard.KeyboardFactory
import io.toodlebot.backend.model.Toodle
import io.toodlebot.backend.service.DatabaseService
import io.toodlebot.backend.service.Env
import java.util.*

fun setup(databaseService: DatabaseService): Bot {
    val bot = Bot.createPolling(token = Env.botToken, username = Env.botUsername)
    //println("Server URL for setup: ${Env.domain}/setup")

    bot.onCommand("/start") { (msg, value) ->
        if (value == null) {  // Someone creates a Toodle
            bot.sendMessage(
                    chatId = msg.chat.id.toChatId(),
                "Click the button below to create a new Toodle!",
                replyMarkup = InlineKeyboardMarkup(
                    KeyboardFactory.inlineMarkup(
                        listOf(
                            InlineKeyboardButton(
                                "Create Toodle",
                                loginUrl = LoginUrl("${Env.domain}/setup", requestWriteAccess = true)
                            )
                        )
                    )
                )
            )
        } else {  // Bot was added to a group chat
            // TODO: Check that input is not malicious, anyone can enter "/start somethingbad"
            val toodleId = UUID.fromString(value)
            val chatIds = databaseService.getChatIdsOfToodle(toodleId)
            val toodle = databaseService.getToodleById(toodleId)
            if(!chatIds.contains(msg.chat.id)) {
                databaseService.addChatIdToToodle(toodleId, msg.chat.id)
                bot.sendMessage(
                        chatId = msg.chat.id.toChatId(), // ID of the chosen group chat
                    "Answer the Toodle \"${toodle.title}\" with the button below. You can also edit your answer.",
                    replyMarkup = InlineKeyboardMarkup(
                        KeyboardFactory.inlineMarkup(
                            listOf(
                                InlineKeyboardButton(
                                    "Answer Toodle / Edit Answer",
                                    loginUrl = LoginUrl(
                                        "${Env.domain}/answer/$value"
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

/**
 * Sends a message with buttons to share, edit, close and delete the [toodle] to the given [chatId].
 */
suspend fun Bot.sendShareableToodle(chatId: String, toodle: Toodle) {
    this.sendMessage(
            chatId = chatId.toChatId(),
        "Toodle \"${toodle.title}\" created. Use the buttons share, close, edit or delete it. It will automatically expire if there is no activity for one week.",
        replyMarkup = InlineKeyboardMarkup(
            KeyboardFactory.inlineMarkup(
                listOf(
                    InlineKeyboardButton(
                        "Share Toodle",
                        url = "https://t.me/${Env.botUsername}?startgroup=${toodle.id}"
                    ),
                    InlineKeyboardButton(
                        "Edit Toodle",
                        loginUrl = LoginUrl(
                            "${Env.domain}/setup/${toodle.id}"
                        )
                    ),
                    InlineKeyboardButton(
                        "Close Toodle",
                        loginUrl = LoginUrl(
                            "${Env.domain}/close/${toodle.id}"
                        )
                    ),
                    InlineKeyboardButton(
                        "Delete Toodle",
                        loginUrl = LoginUrl(
                                "${Env.domain}/delete/${toodle.id}"
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
suspend fun Bot.sendViewButtonToChats(chatIds: List<Long>, toodle: Toodle) {
    for (chatId in chatIds) {
        this.sendMessage(
                chatId = chatId.toChatId(),
            "The Toodle \"${toodle.title}\" was closed. Use the button to view the results. It'll be deleted automatically in a week.",
            replyMarkup = InlineKeyboardMarkup(
                KeyboardFactory.inlineMarkup(
                    listOf(
                        InlineKeyboardButton(
                            "View Toodle",
                            loginUrl = LoginUrl(
                                "${Env.domain}/view/${toodle.id}"
                            )
                        )
                    )
                )
            )
        )
    }
}