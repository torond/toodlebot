package io.doodlebot.service

import io.doodlebot.backend.model.DoodleInfos
import io.doodlebot.backend.model.NewParticipant
import io.doodlebot.backend.model.Participants
import io.doodlebot.backend.service.DatabaseService
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import java.time.LocalDate

class DatabaseServiceTest {
    private val databaseService = DatabaseService()

    @Test
    fun `Add simple DoodleInfo`() = runBlocking {
        // Given
        // When
        val savedId = databaseService.createDoodle()

        // Then
        val retrieved = databaseService.getDoodle(savedId)
        assertEquals(retrieved?.id, savedId.value)
        assertEquals(retrieved?.numberOfParticipants, 0)

        Unit
    }

    @Test
    fun `Add simple dates`() = runBlocking {
        // Given
        val dates = listOf(LocalDate.parse("2019-03-27"), LocalDate.parse("2019-03-28"), LocalDate.parse("2019-03-29"))

        // When
        val savedIds = databaseService.addDates(dates)

        // Then
        val retrieved = databaseService.getDates(savedIds).map { it?.doodleDate }
        assertTrue(dates.size == retrieved.size
                && dates.containsAll(retrieved)
                && retrieved.containsAll(dates))
    }

    @Test
    fun `Reuse old date entries`() = runBlocking {
        // Given two lists with 4 unique dates
        val dates1 = listOf(LocalDate.parse("2019-03-26"), LocalDate.parse("2019-03-27"), LocalDate.parse("2019-03-28"))
        val dates2 = listOf(LocalDate.parse("2019-03-27"), LocalDate.parse("2019-03-28"), LocalDate.parse("2019-03-29"))

        // When
        val savedIds1 = databaseService.addDatesIfNotExisting(dates1)
        val savedIds2 = databaseService.addDatesIfNotExisting(dates2)

        // Then
        val dateSet = dates1 union dates2
        val savedIdSet = savedIds1 union savedIds2
        val retrieved = databaseService.getDates(savedIdSet.toList()).map { it?.doodleDate }
        println(dateSet)
        println(savedIdSet)
        assertTrue(dateSet.size == savedIdSet.size
                && retrieved.containsAll(dates1)
                && retrieved.containsAll(dates2))

    }

    @Test
    fun `Add Doodle with Dates`() = runBlocking {
        // Given
        val dates = listOf(LocalDate.parse("2019-03-27"), LocalDate.parse("2019-03-28"), LocalDate.parse("2019-03-29"))

        // When
        val savedDoodleId = databaseService.createDoodleFromDates(dates)

        // Then
        val retrievedDates = databaseService.getProposedDatesByDoodleId(savedDoodleId).map { it.doodleDate }
        assertTrue(dates.size == retrievedDates.size
                && dates.containsAll(retrievedDates)
                && retrievedDates.containsAll(dates))

        val retrievedDoodleId = databaseService.getDoodle(EntityID(savedDoodleId, DoodleInfos))?.id // Is importing Tables allowed here?
        assertEquals(savedDoodleId, retrievedDoodleId)
    }

    @Test
    fun `Update Dates`() = runBlocking {
        // Given
        val dates = listOf(LocalDate.parse("2018-03-26"), LocalDate.parse("2018-03-27"))
        val savedDoodleId = databaseService.createDoodleFromDates(dates)

        // When
        val newDates = listOf(LocalDate.parse("2018-03-27"), LocalDate.parse("2018-03-28"), LocalDate.parse("2018-03-29"))
        databaseService.updateDoodleWithDates(savedDoodleId, newDates)

        // Then
        val retrievedDates = databaseService.getProposedDatesByDoodleId(savedDoodleId).map { it.doodleDate }
        assertTrue(newDates.size == retrievedDates.size
                && newDates.containsAll(retrievedDates)
                && retrievedDates.containsAll(newDates))
    }

    @Test
    fun `Add participant`() = runBlocking {
        // Given
        val participant = NewParticipant("a")

        // When
        val savedParticipant = databaseService.addParticipantIfNotExisting(participant)

        // Then
        val retrieved = databaseService.getParticipant(EntityID(savedParticipant.id, Participants))
        assertEquals(retrieved?.id, savedParticipant.id)
        assertEquals(retrieved?.name, participant.name)
    }

    @Test
    fun `Do not add participant with same name twice`() = runBlocking {

    }

    @Test
    fun `Add Doodle with dates and participants`() = runBlocking {
        // Given
        val date1 = LocalDate.parse("2014-03-27")
        val date2 = LocalDate.parse("2014-03-28")
        val date3 = LocalDate.parse("2014-03-29")
        val participantA = NewParticipant("a")
        val participantB = NewParticipant("b")
        val participantC = NewParticipant("c")

        // When
        val savedDoodleId = databaseService.createDoodleFromDates(listOf(date1, date2, date3))
        val savedParticipantA = databaseService.addParticipantIfNotExisting(participantA)
        val savedParticipantB = databaseService.addParticipantIfNotExisting(participantB)
        val savedParticipantC = databaseService.addParticipantIfNotExisting(participantC)
        val dateId1 = databaseService.getDateIdByDate(date1)
        val dateId2 = databaseService.getDateIdByDate(date2)
        val dateId3 = databaseService.getDateIdByDate(date3)
        databaseService.addParticipations(savedDoodleId, savedParticipantA, listOf(date1, date2))
        databaseService.addParticipations(savedDoodleId, savedParticipantB, listOf(date2, date3))
        databaseService.addParticipations(savedDoodleId, savedParticipantC, listOf(date1))

        // Then
        val participantsOnDate1 = databaseService.getParticipantsByDoodleAndDate(savedDoodleId, dateId1)
        val participantsOnDate2 = databaseService.getParticipantsByDoodleAndDate(savedDoodleId, dateId2)
        val participantsOnDate3 = databaseService.getParticipantsByDoodleAndDate(savedDoodleId, dateId3)

        assertTrue(participantsOnDate1.contains(savedParticipantA)
                && participantsOnDate1.contains(savedParticipantC)
                && participantsOnDate1.size == 2)
        assertTrue(participantsOnDate2.contains(savedParticipantA)
                && participantsOnDate2.contains(savedParticipantB)
                && participantsOnDate2.size == 2)
        assertTrue(participantsOnDate3.contains(savedParticipantB)
                && participantsOnDate3.size == 1)



    }
}