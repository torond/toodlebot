package io.doodlebot.backend.model

import org.jetbrains.exposed.sql.Table

object InfoJoinDate: Table() {
    val doodleInfo = reference("doodleInfo", DoodleInfos)
    val doodleDate = reference("doodleDate", DoodleDates)
    override val primaryKey = PrimaryKey(doodleInfo, doodleDate)
}