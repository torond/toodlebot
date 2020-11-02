package io.doodlebot.backend.service

import io.doodlebot.backend.model.DoodleDates
import io.doodlebot.backend.model.DoodleInfos
import io.doodlebot.backend.model.InfoJoinDate
import io.doodlebot.backend.model.Participations
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.SchemaUtils.drop
import java.sql.Connection

object DatabaseFactory {
    fun init() {
        Database.connect("jdbc:sqlite:./data/data.db", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction {
            drop(DoodleInfos)
            drop(DoodleDates)
            drop(InfoJoinDate)
            drop(Participations)
            create(DoodleInfos)
            create(DoodleDates)
            create(InfoJoinDate)
            create(Participations)
        }
    }


    suspend fun <T> dbQuery(block: suspend () -> T): T = newSuspendedTransaction { block() }
}