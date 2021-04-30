package io.toodlebot.backend.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Participants: IntIdTable() {
    val userId = varchar("userId", 255)
    val toodle = reference("toodle", Toodles, onDelete = ReferenceOption.CASCADE)
}