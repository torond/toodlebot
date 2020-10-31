package io.doodlebot.backend.service

import io.doodlebot.backend.model.*
import io.doodlebot.backend.service.DatabaseFactory.dbQuery
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import java.time.LocalDate
import java.util.*

class DatabaseService {

    suspend fun addDoodle(doodle: NewDoodleInfo): EntityID<UUID> = dbQuery {
        DoodleInfos.insertAndGetId {
            it[numberOfParticipants] = doodle.numberOfParticipants
        }
    }

    suspend fun getDoodle(id: EntityID<UUID>): DoodleInfo? = dbQuery {
        DoodleInfos.select {
            DoodleInfos.id eq id
        }.mapNotNull { toDoodleInfo(it) }.singleOrNull()
    }

    suspend fun addDates(dates: List<LocalDate>): List<EntityID<Int>> = dbQuery {
        DoodleDates.batchInsert(dates) {
            this[DoodleDates.doodleDate] = it
        }
    }.map { it[DoodleDates.id] }

    suspend fun addDatesIfNotExisting(dates: List<LocalDate>): List<EntityID<Int>> {
        var l: MutableList<EntityID<Int>> = mutableListOf()
        for (date in dates) {
            val insertedId = dbQuery {
                DoodleDates.insertIgnoreAndGetId { it[doodleDate] = date }
            } ?: dbQuery {
                DoodleDates.select { (DoodleDates.doodleDate eq date) }
                        .map { row -> row[DoodleDates.id] }
                        .single()
            }
            l.add(insertedId)

        }
        return l
    }

    suspend fun getDate(id: EntityID<Int>): DoodleDate? = dbQuery {
        DoodleDates.select {
            DoodleDates.id eq id.value
        }.mapNotNull { toDoodleDate(it) }.singleOrNull()
    }

    suspend fun getDates(ids: List<EntityID<Int>>): List<DoodleDate?> = ids.map { getDate(it) }

    suspend fun addInfoJoinDate(infoId: EntityID<UUID>, dateIds: List<EntityID<Int>>) = dbQuery {
        InfoJoinDate.batchInsert(dateIds) {
            this[InfoJoinDate.doodleDate] = it
            this[InfoJoinDate.doodleInfo] = infoId
        }
    }

    suspend fun updateInfoJoinDate(infoId: UUID, newDateIds: List<EntityID<Int>>) {
        val oldDateIds = getDateIdsByDoodleId(infoId)
        val toBeDeleted = oldDateIds.minus(newDateIds)
        val toBeAdded = newDateIds.minus(oldDateIds)
        dbQuery {
            InfoJoinDate.deleteWhere { (InfoJoinDate.doodleInfo eq infoId) and (InfoJoinDate.doodleDate inList toBeDeleted) }
            InfoJoinDate.batchInsert(toBeAdded) {
                this[InfoJoinDate.doodleDate] = it
                this[InfoJoinDate.doodleInfo] = infoId
            }
        }
    }

    suspend fun addDoodleWithDates(doodle: NewDoodleInfo, dates: List<LocalDate>): UUID {
        val doodleId = addDoodle(doodle)
        val dateIds = addDatesIfNotExisting(dates)
        addInfoJoinDate(doodleId, dateIds)

        return doodleId.value
    }

    suspend fun getDatesByDoodleId(id: UUID): List<DoodleDate>? = dbQuery {
        (DoodleInfos crossJoin InfoJoinDate crossJoin DoodleDates).select {
            (DoodleInfos.id eq id) and (DoodleInfos.id eq InfoJoinDate.doodleInfo) and (DoodleDates.id eq InfoJoinDate.doodleDate)
        }.map { toDoodleDate(it) }
    }

    // Should the return type be optional?
    suspend fun getDateIdsByDoodleId(id: UUID): List<EntityID<Int>> = dbQuery {
        (DoodleInfos crossJoin InfoJoinDate crossJoin DoodleDates).select {
            (DoodleInfos.id eq id) and (DoodleInfos.id eq InfoJoinDate.doodleInfo) and (DoodleDates.id eq InfoJoinDate.doodleDate)
        }.map { it[DoodleDates.id] }
    }

    suspend fun updateDoodleWithDates(id: UUID, dates: List<LocalDate>) {
        // TODO: Remove globally unused date entries
        val dateIds = addDatesIfNotExisting(dates)
        updateInfoJoinDate(id, dateIds)
    }

    private fun toDoodleInfo (row: ResultRow): DoodleInfo =
        DoodleInfo(
            id = row[DoodleInfos.id].value,
            numberOfParticipants = row[DoodleInfos.numberOfParticipants]
        )

    private fun toDoodleDate (row: ResultRow): DoodleDate =
        DoodleDate(
            id = row[DoodleDates.id].value,
            doodleDate = row[DoodleDates.doodleDate]
        )

}