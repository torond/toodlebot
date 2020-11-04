package io.doodlebot.backend.model

import org.jetbrains.exposed.sql.Table

object Participations: Table() {
    val doodleInfo = reference("doodleInfo", DoodleInfos)
    val doodleDate = reference("doodleDate", DoodleDates)
    val participant = reference("participant", Participants)
    override val primaryKey = PrimaryKey(doodleInfo, doodleDate, participant)
}

data class Participation(
    val doodleInfo: DoodleInfo,
    val doodleDate: DoodleDate,
    val participant: Participant
)