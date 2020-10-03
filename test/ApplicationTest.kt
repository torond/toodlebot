package io.doodlebot

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import com.github.mustachejava.DefaultMustacheFactory
import io.doodlebot.backend.module
import io.ktor.mustache.Mustache
import io.ktor.mustache.MustacheContent
import io.ktor.gson.*
import io.ktor.features.*
import kotlin.test.*
import io.ktor.server.testing.*

class ApplicationTest {
@   Test
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
}
