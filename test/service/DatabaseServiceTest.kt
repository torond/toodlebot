package io.doodlebot.service

import io.doodlebot.backend.model.DoodleInfos
import io.doodlebot.backend.model.NewDoodleInfo
import io.doodlebot.backend.service.DatabaseService
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import java.time.LocalDate
import java.util.*

class DatabaseServiceTest {
    private val databaseService = DatabaseService()

    @Test
    fun `Add simple DoodleInfo`() = runBlocking {
        //given
        val doodleInfo = NewDoodleInfo()

        //when
        val savedId = databaseService.addDoodle(doodleInfo)

        //then
        val retrieved = databaseService.getDoodle(savedId)
        assertEquals(retrieved?.id, savedId.value)
        assertEquals(retrieved?.numberOfParticipants, doodleInfo.numberOfParticipants)

        Unit
    }

    @Test
    fun `Add simple dates`() = runBlocking {
        //given
        val dates = listOf(LocalDate.parse("2019-03-27"), LocalDate.parse("2019-03-28"), LocalDate.parse("2019-03-29"))

        //when
        val savedIds = databaseService.addDates(dates)

        //then
        val retrieved = databaseService.getDates(savedIds).map { it?.doodleDate }
        assertTrue(dates.size == retrieved.size
                && dates.containsAll(retrieved)
                && retrieved.containsAll(dates))
    }

    @Test
    fun `Reuse old date entries`() = runBlocking {
        //given two lists with 4 unique dates
        val dates1 = listOf(LocalDate.parse("2019-03-26"), LocalDate.parse("2019-03-27"), LocalDate.parse("2019-03-28"))
        val dates2 = listOf(LocalDate.parse("2019-03-27"), LocalDate.parse("2019-03-28"), LocalDate.parse("2019-03-29"))

        //when
        val savedIds1 = databaseService.addDatesIfNotExisting(dates1)
        val savedIds2 = databaseService.addDatesIfNotExisting(dates2)

        //then
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
        //given
        val doodleInfo = NewDoodleInfo()
        val dates = listOf(LocalDate.parse("2019-03-27"), LocalDate.parse("2019-03-28"), LocalDate.parse("2019-03-29"))

        //when
        val savedDoodleId = databaseService.addDoodleWithDates(doodleInfo, dates)

        //then
        val retrievedDates = databaseService.getDatesByDoodleId(savedDoodleId).map { it.doodleDate }
        assertTrue(dates.size == retrievedDates.size
                && dates.containsAll(retrievedDates)
                && retrievedDates.containsAll(dates))

        val retrievedDoodleId = databaseService.getDoodle(EntityID(savedDoodleId, DoodleInfos))?.id // Is importing Tables allowed here?
        assertEquals(savedDoodleId, retrievedDoodleId)
    }

    @Test
    fun `Update Dates`() = runBlocking {
        //given
        val doodleInfo = NewDoodleInfo()
        val dates = listOf(LocalDate.parse("2018-03-26"), LocalDate.parse("2018-03-27"))
        val savedDoodleId = databaseService.addDoodleWithDates(doodleInfo, dates)

        //when
        val newDates = listOf(LocalDate.parse("2018-03-27"), LocalDate.parse("2018-03-28"), LocalDate.parse("2018-03-29"))
        databaseService.updateDoodleWithDates(savedDoodleId, newDates)

        //Then
        val retrievedDates = databaseService.getDatesByDoodleId(savedDoodleId).map { it.doodleDate }
        assertTrue(newDates.size == retrievedDates.size
                && newDates.containsAll(retrievedDates)
                && retrievedDates.containsAll(newDates))
    }
}