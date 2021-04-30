package io.toodlebot.backend.model

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

/**
 * Generates the table to persist participation information
 */
object Participations: Table() {
    val date = reference("date", Dates)
    val participant = reference("participant", Participants)
    val toodle = reference("toodle", Toodles, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(date, participant)
}

/**
 * Holds the metadata of a participation.
 * Note that the properties are only entity IDs.
 */
data class Participation(
        val dateId: EntityID<Int>,
        val participantId: EntityID<Int>
)