package io.doodlebot.backend.model

import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object Toodles : UUIDTable() {
    val title = varchar("title", 255)
    val isClosed = bool("isClosed")
    val numberOfParticipants = integer("numberOfParticipants")
    val adminUsername = varchar("adminUsername", 255)
}

data class Toodle(
        val title: String,
        val id: UUID,
        val isClosed: Boolean = false,
        val numberOfParticipants: Int = 0,
        val adminUsername: String
)