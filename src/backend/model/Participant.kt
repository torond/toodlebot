package io.doodlebot.backend.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Participants: IntIdTable() {
    val name = varchar("username", 255)
    val toodle = reference("toodle", Toodles, onDelete = ReferenceOption.CASCADE)
}