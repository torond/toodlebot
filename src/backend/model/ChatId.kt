package io.doodlebot.backend.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.`java-time`.date
import java.time.LocalDate

object Chats : IntIdTable() {
    val chatId = long("chatId")
    val doodleInfo = Chats.reference("doodleInfo", DoodleInfos)
}