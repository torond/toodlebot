package io.doodlebot.bot

import com.elbekD.bot.Bot
import com.elbekD.bot.feature.chain.chain
import com.elbekD.bot.feature.chain.terminateChain
import com.elbekD.bot.types.InlineKeyboardButton
import com.elbekD.bot.types.InlineKeyboardMarkup
import com.elbekD.bot.types.LoginUrl
import com.elbekD.bot.util.keyboard.KeyboardFactory
import java.io.FileInputStream
import java.util.*

// TODO: This is hacky
private var LOCAL_IP = ""

fun setup(): Bot {
    val props = Properties()
    val inputStream = FileInputStream("environment.properties")
    props.load(inputStream)
    LOCAL_IP = props.getProperty("local_ip")

    val bot = Bot.createPolling(props.getProperty("bot_name"), props.getProperty("bot_token"))
    val setupKeyboard = InlineKeyboardMarkup(
        KeyboardFactory.inlineMarkup(
            listOf(
                InlineKeyboardButton(
                    "Create Doodle",
                    login_url = LoginUrl("http://${props.getProperty("local_ip")}:8088/setup", request_write_access = true)
                )
            )
        )
    )
    println("Server URL for setup: http://${props.getProperty("local_ip")}:8088/setup")

    bot.onCommand("/start") { msg, value ->
        if (value != null) {
            println(value)
            bot.sendMessage(
                msg.chat.id,
                "Answer the Doodle with the button below.",
                markup = InlineKeyboardMarkup(
                    KeyboardFactory.inlineMarkup(
                        listOf(
                            InlineKeyboardButton(
                                "Answer Doodle",
                                login_url = LoginUrl("http://${props.getProperty("local_ip")}:8088/answer/$value", request_write_access = true)
                            )
                        )
                    )
                )
            )
        } else {
            bot.sendMessage(
                msg.chat.id,
                "Click the button below! Chat id: ${msg.chat.id}",
                markup = setupKeyboard
            )
        }

    }

    return bot
}

fun Bot.sendShareableDoodle(chatId: String, doodleId: String) {
    this.sendMessage(
        chatId,
        "Doodle created. Use the buton below to share the Doodle to a group.",
        markup = InlineKeyboardMarkup(
            KeyboardFactory.inlineMarkup(
                listOf(
                    InlineKeyboardButton(
                        "Share Doodle",
                        switch_inline_query = "Answer Doodle",
                        login_url = LoginUrl("http://${LOCAL_IP}:8088/answer/$doodleId",
                            request_write_access = true)
                    )
                )
            )
        )
    )

}