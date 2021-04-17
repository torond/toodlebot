package io.doodlebot.bot

import com.elbekD.bot.Bot
import com.elbekD.bot.feature.chain.chain
import com.elbekD.bot.feature.chain.terminateChain
import com.elbekD.bot.types.InlineKeyboardButton
import com.elbekD.bot.types.InlineKeyboardMarkup
import com.elbekD.bot.util.keyboard.KeyboardFactory

fun setup(bot_name: String, bot_token: String, local_ip: String): Bot {
    val bot = Bot.createPolling(bot_name, bot_token)
    val setupKeyboard = InlineKeyboardMarkup(
        KeyboardFactory.inlineMarkup(
            listOf(
                InlineKeyboardButton(
                    "Create Doodle",
                    "http://${local_ip}:8088/setup"
                )
            )
        )
    )
    println("Server URL for setup: http://${local_ip}:8088/setup")

    bot.onCommand("/start") { msg, _ ->
        bot.sendMessage(
            msg.chat.id,
            "Click the button below!",
            markup = setupKeyboard
        )
    }

    return bot
}