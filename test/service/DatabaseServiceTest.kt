package io.doodlebot.service

import io.doodlebot.backend.model.NewParticipant
import io.doodlebot.backend.model.Participants
import io.doodlebot.backend.service.DatabaseService
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import java.time.LocalDate
import kotlin.test.*

class DatabaseServiceTest {
    private val databaseService = DatabaseService()

    @Test
    fun `Create Doodle from dates`() = runBlocking {
        // Given
        val dates = listOf(LocalDate.parse("2019-03-27"), LocalDate.parse("2019-03-28"), LocalDate.parse("2019-03-29"))

        // When
        val savedDoodle = databaseService.createDoodleFromDates(dates)
        val retrievedDates = databaseService.getProposedDatesByDoodleId(savedDoodle.id).map { it.doodleDate }
        val retrievedDoodle = databaseService.getDoodleById(savedDoodle.id)

        // Then
        assertTrue(dates.size == retrievedDates.size
                && dates.containsAll(retrievedDates)
                && retrievedDates.containsAll(dates))
        assertEquals(savedDoodle.id, retrievedDoodle.id)
    }

    @Test
    fun `Update proposed dates`() = runBlocking {
        // Given
        val dates = listOf(LocalDate.parse("2018-03-26"), LocalDate.parse("2018-03-27"))
        val savedDoodle = databaseService.createDoodleFromDates(dates)
        val newDates = listOf(LocalDate.parse("2018-03-27"), LocalDate.parse("2018-03-28"), LocalDate.parse("2018-03-29"))

        // When
        databaseService.updateDatesOfDoodle(savedDoodle.id, newDates)

        // Then
        val retrievedDates = databaseService.getProposedDatesByDoodleId(savedDoodle.id).map { it.doodleDate }
        assertTrue(newDates.size == retrievedDates.size
                && newDates.containsAll(retrievedDates)
                && retrievedDates.containsAll(newDates))
    }

    @Test
    fun `Participate in a Doodle`() = runBlocking {
        // Given
        val dates = listOf(LocalDate.parse("2019-03-27")!!, LocalDate.parse("2019-03-28")!!, LocalDate.parse("2019-03-29")!!)
        val yesDates = dates.subList(0, 2)
        val doodle = databaseService.createDoodleFromDates(dates)
        val newParticipant = NewParticipant("a")

        // When
        val savedParticipant = databaseService.addParticipantIfNotExisting(newParticipant)
        databaseService.addParticipations(doodle.id, savedParticipant, yesDates)
        val participations = databaseService.getParticipationsByDoodleId(doodle.id)

        //Then
        assertEquals(newParticipant.name, savedParticipant.name)
        for (date in dates) {
            println(participations[date])
            if (date in yesDates) {
                assertTrue(participations.getValue(date).contains(EntityID(savedParticipant.id, Participants)))
            } else {
                assertTrue(participations[date] == null)
            }
        }
    }

    @Test
    fun `Update participations to a Doodle`() = runBlocking {
        // Given
        val dates = listOf(LocalDate.parse("2017-03-27")!!, LocalDate.parse("2017-03-28")!!, LocalDate.parse("2017-03-29")!!)
        val yesDates = dates.subList(0, 2)
        val yesDatesToBeUpdated = dates.subList(1, 3)
        val doodle = databaseService.createDoodleFromDates(dates)
        val newParticipant = NewParticipant("b")
        val savedParticipant = databaseService.addParticipantIfNotExisting(newParticipant)
        databaseService.addParticipations(doodle.id, savedParticipant, yesDates)
        val savedYesDates = databaseService.getParticipationsByDoodleId(doodle.id)
                .filter {it.value.contains(EntityID(savedParticipant.id, Participants)) }
                .map { it.key }

        // Before
        assertTrue(yesDates.size == savedYesDates.size
                && yesDates.containsAll(savedYesDates)
                && savedYesDates.containsAll(yesDates))

        // When
        databaseService.updateParticipations(doodle.id, savedParticipant, yesDatesToBeUpdated)
        val updatedYesDates = databaseService.getParticipationsByDoodleId(doodle.id)
                .filter {it.value.contains(EntityID(savedParticipant.id, Participants)) }
                .map { it.key }

        //Then
        assertTrue(yesDatesToBeUpdated.size == updatedYesDates.size
                && yesDatesToBeUpdated.containsAll(updatedYesDates)
                && updatedYesDates.containsAll(yesDatesToBeUpdated))
    }

    @Test
    fun `Close a Doodle`() = runBlocking {
        // Given
        val dates = listOf(LocalDate.parse("2016-03-27")!!, LocalDate.parse("2016-03-28")!!, LocalDate.parse("2016-03-29")!!)
        val finalDates = dates.subList(0, 2)
        val doodle = databaseService.createDoodleFromDates(dates)

        // Before
        assertFalse(databaseService.getDoodleById(doodle.id).isClosed)

        // When
        databaseService.markDatesAsFinal(doodle.id, finalDates)
        databaseService.markDoodleAsClosed(doodle.id)
        val savedFinalDates = databaseService.getFinalDatesByDoodleId(doodle.id).map { it.doodleDate }

        //Then
        assertTrue(finalDates.size == savedFinalDates.size
                        && finalDates.containsAll(savedFinalDates)
                        && savedFinalDates.containsAll(finalDates)
        )
        assertTrue(databaseService.getDoodleById(doodle.id).isClosed)
    }

    @Test(expected=IllegalStateException::class)
    fun `Trying to change a closed Doodle should throw IllegalStateException`() = runBlocking {
        // Given
        val dates = listOf(LocalDate.parse("2015-03-27")!!)
        val newDates = listOf(LocalDate.parse("2015-03-01")!!)
        val doodle = databaseService.createDoodleFromDates(dates)
        val newParticipant = NewParticipant("c")
        val savedParticipant = databaseService.addParticipantIfNotExisting(newParticipant)
        databaseService.addParticipations(doodle.id, savedParticipant, dates)
        databaseService.markDoodleAsClosed(doodle.id)

        // When Then
        assertFails { databaseService.updateDatesOfDoodle(doodle.id, newDates) }
        assertFails { databaseService.updateParticipations(doodle.id, savedParticipant, newDates) }
        assertTrue(databaseService.getProposedDatesByDoodleId(doodle.id) == dates)
        assertEquals(databaseService.getParticipationsByDoodleId(doodle.id)[dates.single()]!!.single().value, savedParticipant.id)
    }

    /*
    * @Test
    * fun `getDateIdsByDates should throw exception if a date does not exist`()
    *
    * @Test
    * fun `methods throwing NotFoundException should throw them if Doodle does not exist`()
    * */

    /*@Test
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
    }*/
}