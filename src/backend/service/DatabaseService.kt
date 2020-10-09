package io.doodlebot.backend.service

import io.doodlebot.backend.model.DoodleInfo
import io.doodlebot.backend.model.DoodleInfos
import io.doodlebot.backend.model.NewDoodleInfo
import io.doodlebot.backend.service.DatabaseFactory.dbQuery
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class DatabaseService {

    suspend fun getDoodle(id: UUID): DoodleInfo? = dbQuery {
        DoodleInfos.select {
            DoodleInfos.id eq id
        }.mapNotNull { toDoodleInfo(it) }.singleOrNull()
    }

    suspend fun addDoodle(doodle: NewDoodleInfo): DoodleInfo {
        val id = dbQuery {
            DoodleInfos.insertAndGetId {
                it[numberOfParticipants] = doodle.numberOfParticipants
            }
        }
        return getDoodle(id.value)!!
    }

    private fun toDoodleInfo (row: ResultRow): DoodleInfo =
        DoodleInfo(
            id = row[DoodleInfos.id].value,
            numberOfParticipants = row[DoodleInfos.numberOfParticipants]
        )

}