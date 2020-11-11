package io.doodlebot

import io.doodlebot.backend.module
import io.ktor.http.*
import io.ktor.server.testing.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun `Setup page should show relevant elements`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/setup").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertTrue(response.content!!.contains("flatpickr"))
                assertTrue(response.content!!.contains("Confirm"))
                assertTrue(response.content!!.contains("Cancel"))
            }
        }
    }

    @Test
    fun `Return bad request if doodleId has no doodle, transform NotFoundException to 404 Not Found`() {
        // Given
        val randomUuid = UUID.randomUUID()

        // When
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/answer/$randomUuid").apply {
                //Then
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    @Test
    fun `getUuid should return Bad Request if null`() {
        // Given
        withTestApplication({ module(testing = true) }) {
            // When
            handleRequest(HttpMethod.Get, "/answer/").apply {
                //Then
                assertEquals(HttpStatusCode.BadRequest, response.status())
            }
        }
    }

    /*
    * `Return bad request if doodleId has no doodle (on every endpoint)`
    * `Answers with yesDates outside of the proposed dates should return specific status`
    * `Malformed UUID should return Bad Request`
    * `Changing closed Doodle should return Forbidden`
    *
    *
    *
    *
    * `Throw if doodleId is null` / `has wrong format`
    * `Throw if date list is null` / `dates have wrong format`
    * -> Basically check if correct Exceptions are thrown as expected or if method works
    * */
}


