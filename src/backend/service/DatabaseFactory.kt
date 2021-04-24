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
    val dummyDateId: EntityID<Int>
    init {
        println("Initializin database")
        Database.connect("jdbc:sqlite:./data/data.db", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction {
            drop(DoodleInfos)
            drop(DoodleDates)
            drop(InfoJoinDate)
            drop(Participations)
            drop(Participants)
            drop(Chats)
            create(DoodleInfos)
            create(DoodleDates)
            create(InfoJoinDate)
            create(Participations)
            create(Participants)
            create(Chats)
        }
        // Insert dummy value for marking a successful answer to a Doodle
        dummyDateId = transaction {
            DoodleDates.insertAndGetId { it[doodleDate] = LocalDate.parse("0001-01-01", DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T = newSuspendedTransaction { block() }
}