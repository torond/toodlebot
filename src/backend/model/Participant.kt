package io.doodlebot.backend.model

import org.jetbrains.exposed.dao.id.IntIdTable

object Participants: IntIdTable() {
    val name = varchar("username", 255)
    init {
        index(true, name)  // Maybe there's a unique Telegram ID if user changes their name
    }
}

data class Participant(
        val id: Int,
        val name: String
)

data class NewParticipant(
        val name: String
)