package io.github.yutakax17.advancedhelloworld.messages

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.yutakax17.advancedhelloworld.messages.database.MessagesDatabase
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals

class MessagesMigrationTest {
    @Test
    fun `version one data migrates to normalized timeline`() {
        val path = Files.createTempFile("messages-migration-", ".db")
        try {
            val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
            driver
                .execute(
                    identifier = null,
                    sql =
                        """
                        CREATE TABLE message (
                            local_id TEXT NOT NULL PRIMARY KEY,
                            remote_id TEXT UNIQUE,
                            text TEXT NOT NULL,
                            created_at_local INTEGER NOT NULL,
                            created_at_server TEXT,
                            sync_state TEXT NOT NULL,
                            attempt_count INTEGER NOT NULL DEFAULT 0,
                            last_error TEXT
                        )
                        """.trimIndent(),
                    parameters = 0,
                ).value
            driver
                .execute(
                    identifier = null,
                    sql =
                        """
                        CREATE INDEX message_created_at
                        ON message(created_at_local DESC, local_id)
                        """.trimIndent(),
                    parameters = 0,
                ).value
            driver
                .execute(
                    identifier = null,
                    sql =
                        """
                        INSERT INTO message
                        VALUES ('legacy', NULL, 'saved', 42, '2026-08-03T00:00:00Z', 'SYNCED', 0, NULL)
                        """.trimIndent(),
                    parameters = 0,
                ).value

            MessagesDatabase.Schema.migrate(driver, oldVersion = 1L, newVersion = 2L).value
            val database = MessagesDatabase(driver)

            assertEquals(
                42L,
                database.messagesQueries
                    .selectAll()
                    .executeAsOne()
                    .timeline_at,
            )
            driver.close()
        } finally {
            path.deleteIfExists()
        }
    }
}
