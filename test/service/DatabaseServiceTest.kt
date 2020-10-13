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
    fun `Add Doodle with Dates`() = runBlocking {
        //given
        val doodleInfo = NewDoodleInfo()
        val dates = listOf(LocalDate.parse("2019-03-27"), LocalDate.parse("2019-03-28"), LocalDate.parse("2019-03-29"))

        //when
        val savedDoodleId = databaseService.addDoodleWithDates(doodleInfo, dates)

        //then
        val retrievedDates = databaseService.getDatesByDoodleId(savedDoodleId)?.map { it.doodleDate }
        assertTrue(dates.size == retrievedDates?.size
                && dates.containsAll(retrievedDates)
                && retrievedDates.containsAll(dates))

        val retrievedDoodleId = databaseService.getDoodle(EntityID(savedDoodleId, DoodleInfos))?.id // Is importing Tables allowed here?
        assertEquals(savedDoodleId, retrievedDoodleId)
    }
}