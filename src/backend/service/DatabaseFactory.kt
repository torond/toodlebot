package io.doodlebot.backend.service

import io.doodlebot.backend.model.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.SchemaUtils.drop
import org.jetbrains.exposed.sql.insertAndGetId
import java.sql.Connection
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DatabaseFactory {
    init {
        println("Initializing database")
        Database.connect("jdbc:sqlite:./data/data.db?foreign_keys=on", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction {
            drop(Toodles)
            drop(Dates)
            drop(Participants)
            drop(Participations)
            drop(Chats)
            create(Toodles)
            create(Dates)
            create(Participants)
            create(Participations)
            create(Chats)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T = newSuspendedTransaction { block() }
}