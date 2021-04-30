package io.toodlebot.backend.service

import io.toodlebot.backend.model.*
import io.toodlebot.backend.service.DatabaseFactory.dbQuery
import io.ktor.features.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import java.time.LocalDate
import java.util.*

class DatabaseService {

    /**
     * Inserts a new [Toodle] object and links the corresponding [dates].
     * Returns the created [Toodle] object for accessing its id.
     */
    suspend fun createToodleFromDates(dates: List<LocalDate>, title: String, adminUserId: String): Toodle {
        val toodleId = dbQuery {
            Toodles.insertAndGetId {
                it[this.title] = title
                it[this.isClosed] = Op.FALSE
                it[this.numberOfParticipants] = 0
                it[this.adminUserId] = adminUserId
                it[this.expirationDate] = LocalDate.now().plusWeeks(1)
            }
        }

        dbQuery {
            Dates.batchInsert(dates) {
                this[Dates.date] = it
                this[Dates.toodle] = toodleId
                this[Dates.isFinal] = Op.FALSE
            }
        }
        return getToodleById(toodleId.value)
    }

    /**
     * Returns the [Toodle] corresponding to the given [toodleId].
     */
    suspend fun getToodleById(toodleId: UUID): Toodle = dbQuery {
        Toodles.select {
            Toodles.id eq toodleId
        }.mapNotNull { toToodle(it) }.singleOrNull()
            ?: throw NotFoundException("Toodle with id $toodleId not found")
    }

    /**
     * Updates the proposed dates of the Toodle corresponding to [toodleId] to be [newDates].
     */
    suspend fun updateDatesOfToodle(toodleId: UUID, newDates: List<LocalDate>) {
        val oldDates = getProposedDatesByToodleId(toodleId)
        val toBeDeleted = oldDates.minus(newDates)
        val toBeAdded = newDates.minus(oldDates)

        dbQuery {
            checkToodleIsOpen(toodleId)
            val toBeDeletedIds = getDateIdsByDates(toodleId, toBeDeleted)
            Participations.deleteWhere { (Participations.date inList toBeDeletedIds) }
            Dates.deleteWhere { (Dates.toodle eq toodleId) and (Dates.date inList toBeDeleted) }
            Dates.batchInsert(toBeAdded) {
                this[Dates.date] = it
                this[Dates.toodle] = toodleId
                this[Dates.isFinal] = Op.FALSE
            }
        }
    }

    /**
     * Updates the title of the Toodle corresponding to [toodleId] to be [newTitle] if it changed.
     */
    suspend fun updateTitleOfToodle(toodleId: UUID, newTitle: String) = dbQuery {
        Toodles.update({ Toodles.id eq toodleId }) {
            it[title] = newTitle
        }
    }

    /**
     * Returns the proposed dates of the Toodle corresponding to [toodleId].
     * TODO: Use .slice(Dates.date) or similar to directly return a LocalDate
     */
    suspend fun getProposedDatesByToodleId(toodleId: UUID): List<LocalDate> = dbQuery {
        checkToodleExists(toodleId)
        Dates.select {
            (Dates.toodle eq toodleId)
        }.map { row -> row[Dates.date] }
    }

    /**
     * Returns the final dates of the Toodle corresponding to [toodleId].
     */
    suspend fun getFinalDatesByToodleId(toodleId: UUID): List<LocalDate> = dbQuery {
        checkToodleExists(toodleId)
        Dates.select {
            (Dates.toodle eq toodleId) and (Dates.isFinal eq Op.TRUE)
        }.map { row -> row[Dates.date] }
    }

    /**
     * Adds a participant with the given [userId] to the Toodle corresponding to [toodleId].
     * Adding a participant means that they will count towards numberOfParticipants.
     */
    suspend fun addParticipantToToodle(toodleId: UUID, userId: String) = dbQuery {
        dbQuery {  // Not really needed as it's just the count of participants with toodleId
            Toodles.update({ Toodles.id eq toodleId }) {
                with(SqlExpressionBuilder) {
                    it[numberOfParticipants] = numberOfParticipants + 1
                }
            }
        }
        Participants.insertAndGetId {
            it[this.userId] = userId
            it[this.toodle] = toodleId
        }
    }

    /**
     * Returns the [EntityID]s corresponding to the given [dates].
     */
    private suspend fun getDateIdsByDates(toodleId: UUID, dates: List<LocalDate>): List<EntityID<Int>> {
        val dateIds = dates.map {
            dbQuery {
                Dates.select { (Dates.toodle eq toodleId) and (Dates.date eq it) }
                    .map { row -> row[Dates.id] }
                    .single()
            }
        }
        if (dateIds.size != dates.size) throw NotFoundException("One ore more dates not found, given: $dates, found ids: $dateIds")
        return dateIds
    }

    /**
     * Returns true iff the participant with [userId] has not yet participated in the Toodle corresponding to
     * [toodleId], i.e. if [addParticipantToToodle] was not executed.
     */
    suspend fun notYetParticipating(toodleId: UUID, userId: String): Boolean = dbQuery {
        Participants.select {
            (Participants.toodle eq toodleId) and (Participants.userId eq userId)
        }.empty()
    }

    /**
     * Updates participation entries for the participant with [participantId] to the Toodle corresponding to [toodleId]
     * to be [newDates]. Also works if there are no prior participations, thus it also acts as an addParticipations
     * method.
     */
    suspend fun updateParticipations(toodleId: UUID, participantId: EntityID<Int>, newDates: List<LocalDate>) {
        // Assuming [newDates] is a sublist of proposedDates, this is enforced in Server.kt
        val newDateIds = getDateIdsByDates(toodleId, newDates)
        val participations = getParticipationMap(toodleId)
        val oldDates = participations
            .filter { it.value.contains(participantId) }
            .map { it.key }
        val oldDateIds = getDateIdsByDates(toodleId, oldDates)
        val toBeDeleted = oldDateIds.minus(newDateIds)
        val toBeAdded = newDateIds.minus(oldDateIds)
        dbQuery {
            checkToodleExists(toodleId)
            checkToodleIsOpen(toodleId)
            Participations.deleteWhere { (Participations.participant eq participantId) and (Participations.date inList toBeDeleted) }
        }
        dbQuery {
            Participations.batchInsert(toBeAdded) {
                this[Participations.date] = it
                this[Participations.participant] = participantId
                this[Participations.toodle] = toodleId
            }
        }
    }

    /**
     * Given a [toodleId] returns the participations of the corresponding Toodle.
     */
    private suspend fun getParticipations(toodleId: UUID): List<Participation> = dbQuery {
        Participations.select {
            (Participations.toodle eq toodleId)
        }.map { row -> toParticipation(row) }
    }

    /**
     * Returns a map of [LocalDate]s to their corresponding participant [EntityID]s with regard to the given [toodleId].
     * Dates without any participants are not added to the returned map.
     */
    suspend fun getParticipationMap(toodleId: UUID): Map<LocalDate, List<EntityID<Int>>> {
        dbQuery {
            checkToodleExists(toodleId)
        }
        val participationGrouped =  getParticipations(toodleId)
                .groupBy({ it.dateId }, { it.participantId })
        return participationGrouped.mapKeys {
                    dbQuery {
                        Dates.select { Dates.id eq it.key }
                                .map { row -> row[Dates.date] }
                                .single()
                    }
                }
    }

    /**
     * Retrieves a participant's ID given its [userId] and the [toodleId] of the Toodle the participant is in.
     * Returns null if participant cannot be found.
     */
    suspend fun getParticipantId(toodleId: UUID, userId: String): EntityID<Int>? = dbQuery {
        Participants.select {
            (Participants.userId eq userId) and (Participants.toodle eq toodleId)
        }.mapNotNull { row -> row[Participants.id] }.singleOrNull()

    }

    /**
     * Retrieves the (already) selected yesDates of a participant with [userId] in a Toodle corresponding to [toodleId].
     * Returns an empty list if the participant does not exist or has not picked any dates.
     */
    suspend fun getYesDatesByToodleIdAndParticipantUserId(toodleId: UUID, userId: String): List<LocalDate> {
        val participations = getParticipationMap(toodleId)
        val participantId = getParticipantId(toodleId, userId) ?: return emptyList()
        return participations.filterValues { participantIds -> participantId in participantIds }
            .map { k -> k.key }
            .toList()
    }

    /**
     * Marks the given [dates] of the Toodle corresponding to [toodleId] as final.
     */
    suspend fun markDatesAsFinal(toodleId: UUID, dates: List<LocalDate>) {
        for (date in dates) {
            dbQuery {
                checkToodleIsOpen(toodleId)
                Dates.update({ (Dates.toodle eq toodleId) and (Dates.date eq date) }) {
                    it[isFinal] = Op.TRUE
                }
            }
        }
    }

    /**
     * Marks the Toodle corresponding to [toodleId] as closed.
     */
    suspend fun markToodleAsClosed(toodleId: UUID) = dbQuery {
        checkToodleIsOpen(toodleId)
        Toodles.update({ Toodles.id eq toodleId }) {
            it[isClosed] = Op.TRUE
        }
    }

    /**
     * Throws [NotFoundException] iff Toodle corresponding to [toodleId] does not exist in the database.
     * Must be called from inside a [dbQuery] block.
     */
    private fun checkToodleExists(toodleId: UUID) {
        if (Toodles.select { Toodles.id eq toodleId }.empty()) {
            throw NotFoundException("Toodle with id $toodleId not found")
        }
    }

    /**
     * Throws [IllegalStateException] iff Toodle corresponding to [toodleId] is marked isClosed.
     * Assumes Toodle exists, thus best used after [checkToodleExists] check.
     * Must be called from inside a [dbQuery] block.
     */
    private fun checkToodleIsOpen(toodleId: UUID) {
        if (Toodles.select { Toodles.id eq toodleId }.map { it[Toodles.isClosed] }.single()) {
            throw IllegalStateException("Toodle with id $toodleId is closed and cannot be changed")
        }
    }

    /**
     * Returns true iff Toodle corresponding to [toodleId] is marked isClosed.
     */
    suspend fun toodleIsClosed(toodleId: UUID): Boolean = dbQuery {
        checkToodleExists(toodleId)
        Toodles.select { Toodles.id eq toodleId }.map { it[Toodles.isClosed] }.single()
    }

    /**
     * Returns true iff [adminUserId] is the admin of the Toodle corresponding to [toodleId].
     */
    suspend fun assertIsAdmin(toodleId: UUID, adminUserId: String) {
        if (dbQuery {
                checkToodleExists(toodleId)
                Toodles.select { Toodles.id eq toodleId }.map { it[Toodles.adminUserId] != adminUserId }
                    .single()
            }) {
            throw BadRequestException("Not an admin, access denied.")
        }
    }

    /**
     * Adds the given [chatId] to the Toodle corresponding to [toodleId].
     */
    suspend fun addChatIdToToodle(toodleId: UUID, chatId: Long) = dbQuery {
        checkToodleExists(toodleId)
        checkToodleIsOpen(toodleId)
        Chats.insertIgnore {
            it[this.toodle] = toodleId
            it[this.chatId] = chatId
        }
    }

    /**
     * Retrieves the chatIds of the Toodle corresponding to [toodleId]
     */
    suspend fun getChatIdsOfToodle(toodleId: UUID): List<Long> = dbQuery {
        checkToodleExists(toodleId)
        Chats.select { (Chats.toodle eq toodleId) }
            .map { row -> row[Chats.chatId] }
    }

    /**
     * Deletes everything related to [toodleId].
     */
    suspend fun deleteToodle(toodleId: UUID) = dbQuery {
        checkToodleExists(toodleId)
        Toodles.deleteWhere {
            Toodles.id eq toodleId
        }
    }

    /**
     * Refreshes the expiration date of a Toodle to one week from now.
     */
    suspend fun refreshExpirationDate(toodleId: UUID) = dbQuery {
        Toodles.update({ Toodles.id eq toodleId }) {
            it[expirationDate] = LocalDate.now().plusWeeks(1)
        }
    }

    /**
     * Deletes all Toodles where the expiration date has passed.
     */
    suspend fun deleteExpiredToodles() = dbQuery {
        Toodles.deleteWhere {
            Toodles.expirationDate less LocalDate.now()
        }
    }

    /**
     * Converts the given [row] to a [Toodle].
     */
    private fun toToodle(row: ResultRow): Toodle =
            Toodle(
                    id = row[Toodles.id].value,
                    title = row[Toodles.title],
                    isClosed = row[Toodles.isClosed],
                    numberOfParticipants = row[Toodles.numberOfParticipants],
                    adminUserId = row[Toodles.adminUserId],
                    expirationDate = row[Toodles.expirationDate]
            )

    /**
     * Converts the given [row] to a [Participation].
     */
    private fun toParticipation(row: ResultRow): Participation =
            Participation(
                    dateId = row[Participations.date],
                    participantId = row[Participations.participant]
            )
}