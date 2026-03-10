package com.novel.writing.assistant.service

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant

object DatabaseService {
    @Volatile
    private var dataSource: HikariDataSource? = null

    fun init() {
        if (dataSource != null) return
        synchronized(this) {
            if (dataSource != null) return

            val requireDb = System.getenv("REQUIRE_DB")?.equals("true", true) == true
            val rawUrl = System.getenv("DB_URL")?.trim().orEmpty()
            val url = rawUrl.ifBlank {
                if (requireDb) {
                    throw IllegalStateException("DB_URL is required")
                }
                LogService.warn("DB_URL is blank, falling back to in-memory H2 database")
                "jdbc:h2:mem:novel;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false"
            }
            val user = System.getenv("DB_USER")?.trim().orEmpty().ifBlank { "sa" }
            val password = System.getenv("DB_PASSWORD")?.trim().orEmpty()

            val hikariConfig = HikariConfig().apply {
                jdbcUrl = url
                username = user
                this.password = password
                maximumPoolSize = (System.getenv("DB_POOL_SIZE")?.toIntOrNull() ?: 10).coerceAtLeast(1)
                isAutoCommit = true
                addDataSourceProperty("cachePrepStmts", "true")
                addDataSourceProperty("prepStmtCacheSize", "250")
                addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            }
            dataSource = HikariDataSource(hikariConfig)
            migrate()
        }
    }

    private fun migrate() {
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS writing_projects (
                        id VARCHAR(36) PRIMARY KEY,
                        title VARCHAR(255) NOT NULL,
                        description TEXT NOT NULL,
                        genre_type VARCHAR(100) NOT NULL,
                        creation_date VARCHAR(64) NOT NULL,
                        last_modified_date VARCHAR(64) NOT NULL,
                        status VARCHAR(50) NOT NULL,
                        word_count INT NOT NULL
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS generation_history (
                        id VARCHAR(36) PRIMARY KEY,
                        project_id VARCHAR(36) NOT NULL,
                        generation_type VARCHAR(50) NOT NULL,
                        input_params TEXT NOT NULL,
                        output_content TEXT NOT NULL,
                        generation_date VARCHAR(64) NOT NULL,
                        duration BIGINT NOT NULL
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS project_configs (
                        id VARCHAR(36) PRIMARY KEY,
                        project_id VARCHAR(36) NOT NULL UNIQUE,
                        genre_type VARCHAR(100) NOT NULL,
                        writing_direction TEXT NOT NULL,
                        max_length INT NOT NULL,
                        creativity_level VARCHAR(20) NOT NULL,
                        timestamp VARCHAR(64) NOT NULL
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS documents (
                        id VARCHAR(36) PRIMARY KEY,
                        project_id VARCHAR(36) NOT NULL,
                        file_name VARCHAR(255) NOT NULL,
                        file_type VARCHAR(20) NOT NULL,
                        file_size BIGINT NOT NULL,
                        upload_date VARCHAR(64) NOT NULL,
                        file_path TEXT NOT NULL,
                        content_hash VARCHAR(64) NOT NULL
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS context_entries (
                        id VARCHAR(36) PRIMARY KEY,
                        project_id VARCHAR(36) NOT NULL,
                        session_id VARCHAR(36) NOT NULL,
                        context_type VARCHAR(100) NOT NULL,
                        context_content TEXT NOT NULL,
                        last_updated VARCHAR(64) NOT NULL
                    )
                    """.trimIndent()
                )
                try {
                    statement.executeUpdate(
                        "CREATE INDEX idx_context_project_session_type_updated ON context_entries(project_id, session_id, context_type, last_updated)"
                    )
                } catch (_: Exception) {
                }
                try {
                    statement.executeUpdate(
                        "CREATE INDEX idx_context_project_updated ON context_entries(project_id, last_updated)"
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    fun <T> withConnection(block: (Connection) -> T): T {
        init()
        val ds = requireNotNull(dataSource) { "Database not initialized" }
        ds.connection.use { connection ->
            return block(connection)
        }
    }

    fun nowString(): String = Instant.now().toString()

    fun ResultSet.getStringOrNull(columnLabel: String): String? {
        val value = getString(columnLabel)
        return if (wasNull()) null else value
    }
}
