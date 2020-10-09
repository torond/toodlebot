package io.doodlebot.backend.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.`java-time`.date
import java.time.LocalDate

object DoodleDates : IntIdTable() {
    val doodleDate = date("date")
}

data class DoodleDate(
    val doodleDate: LocalDate
)

