package io.toodlebot.backend.model

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.`java-time`.date
import java.time.LocalDate
import java.util.*

object Toodles : UUIDTable() {
    val title = varchar("title", 255)
    val isClosed = bool("isClosed")
    val numberOfParticipants = integer("numberOfParticipants")
    val adminUserId = varchar("adminUserId", 255)
    val expirationDate = date("expirationDate")
}

data class Toodle(
        val title: String,
        val id: UUID,
        val isClosed: Boolean = false,
        val numberOfParticipants: Int = 0,
        val adminUserId: String,
        val expirationDate: LocalDate
)