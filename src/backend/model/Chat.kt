package io.toodlebot.backend.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

/**
 * Generates the table to persist chat information
 */
object Chats : IntIdTable() {
    val chatId = long("chatId")
    val toodle = reference("toodle", Toodles, onDelete = ReferenceOption.CASCADE)
}