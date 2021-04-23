package io.doodlebot.backend.service

import io.doodlebot.backend.model.*
import io.doodlebot.backend.service.DatabaseFactory.dbQuery
import io.ktor.features.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import java.time.LocalDate
import java.util.*

class DatabaseService {

    // TODO: Add adminId support
    /**
     * Inserts a new [DoodleInfo] object and links the corresponding [dates].
     * Returns the created [DoodleInfo] object for accessing its id.
     */
    suspend fun createDoodleFromDates(dates: List<LocalDate>, adminUsername: String): DoodleInfo {
        val doodleId = dbQuery {
            DoodleInfos.insertAndGetId {
                it[this.isClosed] = Op.FALSE
                it[this.numberOfParticipants] = 0
                it[this.adminUsername] = adminUsername
            }
        }
        val dateIds = addDatesIfNotExistingAndGetIds(dates)
        dbQuery {
            InfoJoinDate.batchInsert(dateIds) {
                this[InfoJoinDate.doodleDate] = it
                this[InfoJoinDate.doodleInfo] = doodleId
                this[InfoJoinDate.isFinal] = Op.FALSE
            }
        }
        return getDoodleById(doodleId.value)
    }

    /**
     * Returns the [DoodleInfo] corresponding to the given [doodleId].
     */
    suspend fun getDoodleById(doodleId: UUID): DoodleInfo = dbQuery {
        DoodleInfos.select {
            DoodleInfos.id eq doodleId
        }.mapNotNull { toDoodleInfo(it) }.singleOrNull() ?: throw NotFoundException("DoodleInfo with id $doodleId not found")
    }

    /**
     * Inserts [dates] if they do not exist already and returns their [EntityID]s.
     */
    private suspend fun addDatesIfNotExistingAndGetIds(dates: List<LocalDate>): List<EntityID<Int>> {
        val l: MutableList<EntityID<Int>> = mutableListOf()
        for (date in dates) {
            val insertedId = dbQuery {
                DoodleDates.insertIgnoreAndGetId { it[doodleDate] = date }
            } ?: dbQuery { DoodleDates.select { (DoodleDates.doodleDate eq date) }
                    .map { row -> row[DoodleDates.id] }
                    .single()
            }
            l.add(insertedId)
        }
        return l
    }

    /**
     * Updates the proposed dates of the Doodle corresponding to [doodleId] to be [newDates].
     */
    suspend fun updateDatesOfDoodle(doodleId: UUID, newDates: List<LocalDate>) {
        // TODO: Remove globally unused date entries
        val newDateIds = addDatesIfNotExistingAndGetIds(newDates)
        val oldDateIds = getProposedDatesByDoodleId(doodleId).map { EntityID(it.id, DoodleDates) }
        val toBeDeleted = oldDateIds.minus(newDateIds)
        val toBeAdded = newDateIds.minus(oldDateIds)
        dbQuery {
            checkDoodleIsOpen(doodleId)
            Participations.deleteWhere { (Participations.doodleInfo eq doodleId) and (Participations.doodleDate inList toBeDeleted) }
            InfoJoinDate.deleteWhere { (InfoJoinDate.doodleInfo eq doodleId) and (InfoJoinDate.doodleDate inList toBeDeleted) }
            InfoJoinDate.batchInsert(toBeAdded) {
                this[InfoJoinDate.doodleDate] = it
                this[InfoJoinDate.doodleInfo] = doodleId
                this[InfoJoinDate.isFinal] = Op.FALSE
            }
        }
    }

    /**
     * Returns the proposed [DoodleDate]s of the Doodle corresponding to [doodleId].
     * If the list is empty, there is no Doodle with this Throws a [NotFoundException] if an
     */
    suspend fun getProposedDatesByDoodleId(doodleId: UUID): List<DoodleDate> = dbQuery {
        checkDoodleExists(doodleId)
        (DoodleInfos crossJoin InfoJoinDate crossJoin DoodleDates).select {
            (DoodleInfos.id eq doodleId) and (DoodleInfos.id eq InfoJoinDate.doodleInfo) and (DoodleDates.id eq InfoJoinDate.doodleDate)
        }.map { toDoodleDate(it) }
    }

    /**
     * Returns the final [DoodleDate]s of the Doodle corresponding to [doodleId].
     */
    suspend fun getFinalDatesByDoodleId(doodleId: UUID): List<DoodleDate> = dbQuery {
        checkDoodleExists(doodleId)
        (DoodleInfos crossJoin InfoJoinDate crossJoin DoodleDates).select {
            (DoodleInfos.id eq doodleId) and (DoodleInfos.id eq InfoJoinDate.doodleInfo) and (DoodleDates.id eq InfoJoinDate.doodleDate) and (InfoJoinDate.isFinal eq Op.TRUE)
        }.map { toDoodleDate(it) }
    }

    /**
     * Inserts a new [participant] if it does not exists already and returns the [Participant] including its generated id.
     */
    suspend fun addParticipantIfNotExisting(participant: NewParticipant): Participant {
        val id = dbQuery {
            Participants.insertIgnoreAndGetId { it[name] = participant.name }
        } ?: dbQuery {
            Participants.select { (Participants.name eq participant.name) }
                    .map { row -> row[Participants.id] }
                    .single()
        }
        return dbQuery {
            Participants.select {
                Participants.id eq id.value
            }.mapNotNull { toParticipant(it) }.single()
        }
    }

    /**
     * Returns the [EntityID]s of the [DoodleDate]s corresponding to the given [dates].
     */
    private suspend fun getDateIdsByDates(dates: List<LocalDate>): List<EntityID<Int>> {
        val dateIds = dates.map {
            dbQuery {
                DoodleDates.select { (DoodleDates.doodleDate eq it) }
                        .map { row -> row[DoodleDates.id] }
                        .single()
            }
        }
        if (dateIds.size != dates.size) throw NotFoundException("One ore more dates not found, given: $dates, found ids: $dateIds")
        return dateIds
    }

    /**
     * Returns true iff [participant] has not yet answered the Doodle corresponding to [doodleId], i.e. if [addParticipations] was not executed.
     */
    suspend fun hasNotAnswered(doodleId: UUID, participant: Participant): Boolean = dbQuery{
        (DoodleInfos crossJoin Participations).select {
            (DoodleInfos.id eq doodleId) and (DoodleInfos.id eq Participations.doodleInfo) and (Participations.participant eq participant.id) and (Participations.doodleDate eq DatabaseFactory.dummyDateId)
        }.empty()
    }

    /**
     * Adds participation entries for the [dates] and the [participant] to the Doodle corresponding to [doodleId].
     * Adds dummy date to mark that [participant] has answered this Doodle already.
     */
    suspend fun addParticipations(doodleId: UUID, participant: Participant, dates: List<LocalDate>) {
        val dateIds = getDateIdsByDates(dates)
        dbQuery {
            checkDoodleExists(doodleId)
            checkDoodleIsOpen(doodleId)
            Participations.batchInsert(dateIds) {
                this[Participations.doodleDate] = it
                this[Participations.doodleInfo] = doodleId
                this[Participations.participant] = participant.id
            }
            Participations.insert {
                it[this.doodleDate] = DatabaseFactory.dummyDateId
                it[this.doodleInfo] = doodleId
                it[this.participant] = participant.id
            }
        }
        dbQuery {
            DoodleInfos.update({DoodleInfos.id eq doodleId}) {
                with(SqlExpressionBuilder) {
                    it[numberOfParticipants] = numberOfParticipants + 1
                }
            }
        }
    }

    /**
     * Updates participation entries for the [participant] to the Doodle corresponding to [doodleId] to be [newDates].
     */
    suspend fun updateParticipations(doodleId: UUID, participant: Participant, newDates: List<LocalDate>) {
        val newDateIds = getDateIdsByDates(newDates)
        val participations = getParticipationsByDoodleId(doodleId)
        val oldDates = participations
                .filter {it.value.contains(EntityID(participant.id, Participants)) }
                .map { it.key }
        val oldDateIds = getDateIdsByDates(oldDates).minus(DatabaseFactory.dummyDateId)  // Keep dummy date
        val toBeDeleted = oldDateIds.minus(newDateIds)
        val toBeAdded = newDateIds.minus(oldDateIds)
        dbQuery {
            checkDoodleIsOpen(doodleId)
            Participations.deleteWhere { (Participations.doodleInfo eq doodleId) and (Participations.participant eq participant.id) and (Participations.doodleDate inList toBeDeleted) }
        }
        dbQuery {
            Participations.batchInsert(toBeAdded) {
                this[Participations.doodleDate] = it
                this[Participations.doodleInfo] = doodleId
                this[Participations.participant] = participant.id
            }
        }
    }

    /**
     * Given a [doodleId] returns the participations of the corresponding Doodle.
     * The participations consist of a map of [LocalDate]s to their corresponding participant [EntityID]s.
     * Dates without any participants are not added to the returned map.
     */
    suspend fun getParticipationsByDoodleId(doodleId: UUID): Map<LocalDate, List<EntityID<Int>>> = dbQuery {
        checkDoodleExists(doodleId)
        (DoodleInfos crossJoin Participations crossJoin DoodleDates).select {
            (DoodleInfos.id eq doodleId) and (DoodleInfos.id eq Participations.doodleInfo) and (DoodleDates.id eq Participations.doodleDate)
        }.groupBy({it[DoodleDates.doodleDate]}, {it[Participations.participant]})
    }

    /**
     * Retrieves the a participant given its [username].
     */
    suspend fun getParticipantByUsername(username: String): Participant = dbQuery {
        Participants.select {
            Participants.name eq username
        }.mapNotNull { toParticipant(it) }.singleOrNull() ?: throw NotFoundException("Participant with username $username not found")
    }

    /**
     * Retrieves the [EntityID] of a participant given its [username].
     */
    private suspend fun getParticipantIdByUsername(username: String): EntityID<Int> = dbQuery {
        Participants.select {
            Participants.name eq username
        }.mapNotNull { row -> row[Participants.id] }.singleOrNull() ?: throw NotFoundException("Participant with username $username not found")
    }

    /**
     * Retrieves the (already) selected yesDates of a participant with [username] in a Doodle corresponding to [doodleId]
     */
    suspend fun getYesDatesByDoodleIdAndParticipantUsername(doodleId: UUID, username: String): List<LocalDate> {
        val participations = getParticipationsByDoodleId(doodleId)
        val participantId = getParticipantIdByUsername(username)
        return participations.filterValues { participantIds -> participantId in participantIds }
            .map { k -> k.key }
            .toList()
    }

    /**
     * Marks the given [dates] of the Doodle corresponding to [doodleId] as final.
     */
    suspend fun markDatesAsFinal(doodleId: UUID, dates: List<LocalDate>) {
        val dateIds = getDateIdsByDates(dates)
        for (dateId in dateIds) {
            dbQuery {
                checkDoodleIsOpen(doodleId)
                InfoJoinDate.update({(InfoJoinDate.doodleInfo eq doodleId) and (InfoJoinDate.doodleDate eq dateId)}) {
                    it[isFinal] = Op.TRUE
                }
            }
        }
    }

    /**
     * Marks the Doodle corresponding to [doodleId] as closed.
     */
    suspend fun markDoodleAsClosed(doodleId: UUID) = dbQuery {
        checkDoodleIsOpen(doodleId)
        DoodleInfos.update({DoodleInfos.id eq doodleId}) {
            it[isClosed] = Op.TRUE
        }
    }

    /**
     * Throws [NotFoundException] iff Doodle corresponding to [doodleId] does not exist in the database.
     * Must be called from inside a [dbQuery] block.
     */
    private fun checkDoodleExists(doodleId: UUID) {
        if (DoodleInfos.select { DoodleInfos.id eq doodleId }.empty()) {
            throw NotFoundException("DoodleInfo with id $doodleId not found")
        }
    }

    /**
     * Throws [IllegalStateException] iff Doodle corresponding to [doodleId] is marked isClosed.
     * Assumes Doodle exists, thus best used after [checkDoodleExists] check.
     * Must be called from inside a [dbQuery] block.
     */
    private fun checkDoodleIsOpen(doodleId: UUID) {
        if (DoodleInfos.select { DoodleInfos.id eq doodleId }.map { it[DoodleInfos.isClosed] }.single()) {
            throw IllegalStateException("Doodle with id $doodleId is closed and cannot be changed")
        }
    }

    /**
     * Returns true iff Doodle corresponding to [doodleId] is marked isClosed.
     */
    suspend fun doodleIsClosed(doodleId: UUID): Boolean = dbQuery {
        checkDoodleExists(doodleId)
        DoodleInfos.select { DoodleInfos.id eq doodleId }.map { it[DoodleInfos.isClosed] }.single()
    }

    /**
     * Returns true iff [adminUsername] is the admin of the Doodle corresponding to [doodleId].
     */
    suspend fun assertIsAdmin(doodleId: UUID, adminUsername: String) {
        if (dbQuery {
            checkDoodleExists(doodleId)
            DoodleInfos.select { DoodleInfos.id eq doodleId }.map { it[DoodleInfos.adminUsername] != adminUsername }.single()
        }) {
            throw BadRequestException("Not an admin, access denied.")
        }
    }


    /**
     * Converts the given [row] to a [DoodleInfo].
     */
    private fun toDoodleInfo (row: ResultRow): DoodleInfo =
        DoodleInfo(
            id = row[DoodleInfos.id].value,
            isClosed = row[DoodleInfos.isClosed],
            numberOfParticipants = row[DoodleInfos.numberOfParticipants],
            adminUsername = row[DoodleInfos.adminUsername]
        )

    /**
     * Converts the given [row] to a [DoodleDate].
     */
    private fun toDoodleDate (row: ResultRow): DoodleDate =
        DoodleDate(
            id = row[DoodleDates.id].value,
            doodleDate = row[DoodleDates.doodleDate]
        )

    /**
     * Converts the given [row] to a [Participant].
     */
    private fun toParticipant (row: ResultRow): Participant =
        Participant(
            id = row[Participants.id].value,
            name = row[Participants.name]
        )
}