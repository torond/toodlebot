package io.doodlebot.backend.model

import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object DoodleInfos: UUIDTable() {
    val isClosed = bool("isClosed")
    val numberOfParticipants = integer("numberOfParticipants")
    val adminUsername = varchar("adminUsername", 255)
}

data class DoodleInfo(
    val id: UUID,
    val isClosed: Boolean = false,
    val numberOfParticipants: Int = 0,
    val adminUsername: String
)