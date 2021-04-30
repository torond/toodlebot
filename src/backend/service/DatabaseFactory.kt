package io.toodlebot.backend.service

import io.toodlebot.backend.model.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.SchemaUtils.drop
import java.sql.Connection

object DatabaseFactory {
    init {
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