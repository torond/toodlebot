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
            it[isClosed] = doodle.isClosed
            it[numberOfParticipants]  = doodle.numberOfParticipants
        }
    }

    suspend fun getDoodle(id: EntityID<UUID>): DoodleInfo? = dbQuery {
        DoodleInfos.select {
            DoodleInfos.id eq id
        }.mapNotNull { toDoodleInfo(it) }.singleOrNull()
    }

    suspend fun getDoodleByUuid(id: UUID): DoodleInfo = dbQuery {
        DoodleInfos.select {
            DoodleInfos.id eq id
        }.mapNotNull { toDoodleInfo(it) }.single() // TODO: Throws 500 if Doodle does not exist
    }

    suspend fun addDates(dates: List<LocalDate>): List<EntityID<Int>> = dbQuery {
        DoodleDates.batchInsert(dates) {
            this[DoodleDates.doodleDate] = it
        }
    }.map { it[DoodleDates.id] }

    suspend fun getDateIdByDate(date: LocalDate): EntityID<Int> = dbQuery {
        DoodleDates.select { (DoodleDates.doodleDate eq date) }
                .map { row -> row[DoodleDates.id] }
                .single()
    }

    suspend fun addDatesIfNotExisting(dates: List<LocalDate>): List<EntityID<Int>> {
        var l: MutableList<EntityID<Int>> = mutableListOf()
        for (date in dates) {
            val insertedId = dbQuery {
                DoodleDates.insertIgnoreAndGetId { it[doodleDate] = date }
            } ?: getDateIdByDate(date)
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
            this[InfoJoinDate.isFinal] = Op.FALSE
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
                this[InfoJoinDate.isFinal] = Op.FALSE
            }
        }
    }

    suspend fun addDoodleWithDates(doodle: NewDoodleInfo, dates: List<LocalDate>): UUID {
        val doodleId = addDoodle(doodle)
        val dateIds = addDatesIfNotExisting(dates)
        addInfoJoinDate(doodleId, dateIds)

        return doodleId.value
    }

    // This should not return List<DoodleDate>? but List<DoodleDate?> (A list that may be empty)
    suspend fun getDatesByDoodleId(id: UUID): List<DoodleDate> = dbQuery {
        (DoodleInfos crossJoin InfoJoinDate crossJoin DoodleDates).select {
            (DoodleInfos.id eq id) and (DoodleInfos.id eq InfoJoinDate.doodleInfo) and (DoodleDates.id eq InfoJoinDate.doodleDate)
        }.map { toDoodleDate(it) }.ifEmpty { emptyList() }
    }

    suspend fun getFinalDatesByDoodleId(id: UUID): List<DoodleDate> = dbQuery {
        (DoodleInfos crossJoin InfoJoinDate crossJoin DoodleDates).select {
            (DoodleInfos.id eq id) and (DoodleInfos.id eq InfoJoinDate.doodleInfo) and (DoodleDates.id eq InfoJoinDate.doodleDate) and (InfoJoinDate.isFinal eq Op.TRUE)
        }.map { toDoodleDate(it) }.ifEmpty { emptyList() }
    }

    // Should the return type be optional?
    suspend fun getDateIdsByDoodleId(id: UUID): List<EntityID<Int>> = dbQuery {
        (DoodleInfos crossJoin InfoJoinDate crossJoin DoodleDates).select {
            (DoodleInfos.id eq id) and (DoodleInfos.id eq InfoJoinDate.doodleInfo) and (DoodleDates.id eq InfoJoinDate.doodleDate)
        }.map { it[DoodleDates.id] }.ifEmpty { emptyList() }
    }

    suspend fun updateDoodleWithDates(id: UUID, dates: List<LocalDate>) {
        // TODO: Remove globally unused date entries
        val dateIds = addDatesIfNotExisting(dates)
        updateInfoJoinDate(id, dateIds)
    }

    suspend fun addParticipantIfNotExisting(participant: NewParticipant): EntityID<Int> = dbQuery {
            Participants.insertIgnoreAndGetId { it[name] = participant.name }
        } ?: dbQuery {
            Participants.select { (Participants.name eq participant.name) }
                    .map { row -> row[Participants.id] }
                    .single()
        }

    suspend fun getDateIdsByDates(dates: List<LocalDate>): List<EntityID<Int>> {
        return dates.map {
            dbQuery {
                DoodleDates.select { (DoodleDates.doodleDate eq it) }
                        .map { row -> row[DoodleDates.id] }
                        .single()
            }
        }
    }

    suspend fun addParticipations(doodleId: UUID, participantId: EntityID<Int>, dateIds: List<EntityID<Int>>) {
        dbQuery {
            Participations.batchInsert(dateIds) {
                this[Participations.doodleDate] = it
                this[Participations.doodleInfo] = doodleId
                this[Participations.participant] = participantId
            }
        }
        // TODO: Check if below is ok (ok, if participant is new to this Doodle)
        dbQuery {
            DoodleInfos.update({DoodleInfos.id eq doodleId}) {
                with(SqlExpressionBuilder) {
                    it[DoodleInfos.numberOfParticipants] = DoodleInfos.numberOfParticipants + 1
                }
            }
        }
    }

    suspend fun getParticipant(id: EntityID<Int>): Participant? = dbQuery {
        Participants.select {
            Participants.id eq id.value
        }.mapNotNull { toParticipant(it) }.singleOrNull()
    }

    suspend fun getParticipantsByDoodleAndDate(id: UUID, dateId: EntityID<Int>): List<Participant> = dbQuery {
        (DoodleInfos crossJoin Participations crossJoin DoodleDates crossJoin Participants).select {
            (DoodleInfos.id eq id) and (DoodleDates.id eq dateId) and (DoodleInfos.id eq Participations.doodleInfo) and (DoodleDates.id eq Participations.doodleDate) and (Participants.id eq Participations.participant)
        }.map { toParticipant(it) }.ifEmpty { emptyList() }
    }

    /** Returns a list of dates with corresponding participant IDs, does not add dates with no participants */
    suspend fun getParticipations(id: UUID): Map<LocalDate, List<EntityID<Int>>> = dbQuery {
        (DoodleInfos crossJoin Participations crossJoin DoodleDates).select {
            (DoodleInfos.id eq id) and (DoodleInfos.id eq Participations.doodleInfo) and (DoodleDates.id eq Participations.doodleDate)
        }.groupBy({it[DoodleDates.doodleDate]}, {it[Participations.participant]})
    }

    suspend fun markDatesAsFinal(id: UUID, dateIds: List<EntityID<Int>>) {
        for (dateId in dateIds) {
            dbQuery {
                InfoJoinDate.update({(InfoJoinDate.doodleInfo eq id) and (InfoJoinDate.doodleDate eq dateId)}) {
                    it[InfoJoinDate.isFinal] = Op.TRUE
                }
            }
        }
    }

    suspend fun markDoodleAsClosed(id: UUID) = dbQuery {
        DoodleInfos.update({DoodleInfos.id eq id}) {
            it[DoodleInfos.isClosed] = Op.TRUE
        }
    }

    private fun toDoodleInfo (row: ResultRow): DoodleInfo =
        DoodleInfo(
            id = row[DoodleInfos.id].value,
            isClosed = row[DoodleInfos.isClosed],
            numberOfParticipants = row[DoodleInfos.numberOfParticipants]
        )

    private fun toDoodleDate (row: ResultRow): DoodleDate =
        DoodleDate(
            id = row[DoodleDates.id].value,
            doodleDate = row[DoodleDates.doodleDate]
        )

    private fun toParticipant (row: ResultRow): Participant =
        Participant(
            id = row[Participants.id].value,
            name = row[Participants.name]
        )
}