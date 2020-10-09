package io.doodlebot.backend.service

import io.doodlebot.backend.model.DoodleDates
import io.doodlebot.backend.model.DoodleInfos
import io.doodlebot.backend.model.InfoJoinDate
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.SchemaUtils.drop
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import java.sql.Connection
import java.time.LocalDate

object DatabaseFactory {
    fun init() {
        Database.connect("jdbc:sqlite:./data/data.db", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction {
            drop(DoodleInfos)
            drop(DoodleDates)
            drop(InfoJoinDate)
            create(DoodleInfos)
            create(DoodleDates)
            create(InfoJoinDate)
        }
    }


    suspend fun <T> dbQuery(block: suspend () -> T): T = newSuspendedTransaction { block() }
}