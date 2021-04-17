package io.doodlebot.bot

import com.elbekD.bot.Bot
import com.elbekD.bot.feature.chain.chain
import com.elbekD.bot.feature.chain.terminateChain
import com.elbekD.bot.types.InlineKeyboardButton
import com.elbekD.bot.types.InlineKeyboardMarkup
import com.elbekD.bot.util.keyboard.KeyboardFactory

fun setup(local_ip: String): Bot {
    val username = "DateFinderBot"
    val token = "1766857687:AAHmtN907ReQ1PNvSjuW1DNnJtP5-ghGFRA"
    val bot = Bot.createPolling(username, token)
    val setupKeyboard = InlineKeyboardMarkup(KeyboardFactory.inlineMarkup(listOf(InlineKeyboardButton("Create Doodle", "http://${local_ip}:8088/setup"))))
    println("Server URL for setup: http://${local_ip}:8088/setup")

    bot.onCommand("/start") { msg, _ -> bot.sendMessage(msg.chat.id, "Click the button below!", markup = setupKeyboard) }
    bot.onCommand("/s") { msg, _ -> bot.sendMessage(msg.chat.id, "Click the button below!") }

    return bot
}