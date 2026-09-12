package com.linguaai.server.db

import com.linguaai.server.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

/** Flyway migration location, shared by every supported dialect. */
private const val MIGRATION_LOCATION = "classpath:db/migration"

/** HikariCP pool size. Sized for a single instance; scale with it. */
private const val MAX_POOL_SIZE = 10

/**
 * Owns the connection pool and schema migrations. Flyway is the single source
 * of DDL truth — Exposed never auto-creates schema.
 */
object DatabaseFactory {

    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun init(config: AppConfig): HikariDataSource {
        val dataSource = createDataSource(config)
        migrate(dataSource, config.dbUrl)
        Database.connect(dataSource)
        return dataSource
    }

    private fun createDataSource(config: AppConfig): HikariDataSource {
        val driverClass = when {
            config.dbUrl.startsWith("jdbc:h2") -> "org.h2.Driver"
            else -> "com.mysql.cj.jdbc.Driver"
        }
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            driverClassName = driverClass
            maximumPoolSize = MAX_POOL_SIZE
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            poolName = "linguaai-pool"
            validate()
        }
        return HikariDataSource(hikariConfig)
    }

    private fun migrate(dataSource: HikariDataSource, dbUrl: String) {
        // V1 schema and seed are written for both dialects, so H2 and MySQL share
        // one location. This used to be a conditional whose two branches were
        // identical, which read as a dialect difference that did not exist.
        Flyway.configure()
            .dataSource(dataSource)
            .locations(MIGRATION_LOCATION)
            .load()
            .migrate()
        log.info("Database migrations applied")
    }
}

/** Boot-time hook; tests call [DatabaseFactory.init] through the module too. */
fun Application.configureDatabase(config: AppConfig) {
    DatabaseFactory.init(config)
}
