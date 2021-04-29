package io.doodlebot.backend.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.`java-time`.date

object Dates : IntIdTable() {
    val date = date("date")
    val isFinal = bool("isFinal")
    val toodle = reference("toodle", Toodles, onDelete = ReferenceOption.CASCADE)
}