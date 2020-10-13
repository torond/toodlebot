package io.doodlebot.backend.model

import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object DoodleInfos: UUIDTable() {
    val numberOfParticipants = integer("numberOfParticipants")
}

data class DoodleInfo(
    val id: UUID,
    val numberOfParticipants: Int
)

data class NewDoodleInfo(
    val numberOfParticipants: Int = 0
)