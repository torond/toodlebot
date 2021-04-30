package io.toodlebot

import io.toodlebot.backend.module
import io.ktor.http.*
import io.ktor.server.testing.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    /*@Test
    fun `Setup page should show relevant elements`() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/setup").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertTrue(response.content!!.contains("flatpickr"))
                assertTrue(response.content!!.contains("Confirm"))
                assertTrue(response.content!!.contains("Cancel"))
            }
        }
    }*/

    /*@Test
    fun `Return bad request if toodleId has no Toodle, transform NotFoundException to 404 Not Found`() {
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
    }*/

    /*@Test
    fun `Redirect to view if Toodle is closed`() {
        // Given
        withTestApplication({ module(testing = true) }) {
            // When
            val call1 = handleRequest(HttpMethod.Post, "/setup") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("[\"2020-10-01\"]")
            }
            val toodleId = call1.parameters["toodleId"]!!
            handleRequest(HttpMethod.Post, "/close/$toodleId") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("[\"2020-10-01\"]")
            }

            // Then
            // TODO: This does not work!
            // Maybe each handleRequest() resets the DB? Look at log.
            val call2 = handleRequest(HttpMethod.Get, "/answer/$toodleId") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("[\"2020-10-01\"]")
            }
            println(call2.request.uri)
            println(call2.response.headers)
            //assertEquals(call2.response.)
        }
    }*/

    /*
    * `Return bad request if toodleId has no toodle (on every endpoint)`
    * `Answers with yesDates outside of the proposed dates should return specific status`
    * `Malformed UUID should return Bad Request`
    * `Changing closed Toodle should return Forbidden`
    *
    *
    *
    *
    * `Throw if toodleId is null` / `has wrong format`
    * `Throw if date list is null` / `dates have wrong format`
    * -> Basically check if correct Exceptions are thrown as expected or if method works
    * */
}


