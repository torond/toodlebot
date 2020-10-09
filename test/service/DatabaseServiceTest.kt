package io.doodlebot.service

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import com.github.mustachejava.DefaultMustacheFactory
import io.doodlebot.backend.model.DoodleDates
import io.doodlebot.backend.model.DoodleInfos
import io.doodlebot.backend.model.InfoJoinDate
import io.doodlebot.backend.model.NewDoodleInfo
import io.doodlebot.backend.module
import io.doodlebot.backend.service.DatabaseService
import io.ktor.mustache.Mustache
import io.ktor.mustache.MustacheContent
import io.ktor.gson.*
import io.ktor.features.*
import kotlin.test.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertThat
import java.time.LocalDate

class DatabaseServiceTest {
    private val databaseService = DatabaseService()

    @Test
    fun `Add simple DoodleInfo`() = runBlocking {
        //given
        val doodleInfo = NewDoodleInfo(numberOfParticipants = 5)

        //when
        val saved = databaseService.addDoodle(doodleInfo)

        //then
        val retrieved = databaseService.getDoodle(saved.id)
        assertEquals(retrieved, saved)
        assertEquals(retrieved?.id, saved.id)
        assertEquals(retrieved?.numberOfParticipants, saved.numberOfParticipants)

        Unit
    }
}