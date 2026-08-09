package io.github.yutakax17.advancedhelloworld.messages

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.yutakax17.advancedhelloworld.core.Clock
import io.github.yutakax17.advancedhelloworld.core.UuidGenerator
import io.github.yutakax17.advancedhelloworld.messages.database.MessagesDatabase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SqlDelightMessageRepositoryTest {
    @Test
    fun `offline creation atomically persists message and outbox`() =
        runTest {
            databaseFixture().use { fixture ->
                val repository = fixture.repository(UnconfinedTestDispatcher(testScheduler))

                val result = repository.createOffline("  hello offline  ")

                assertEquals("hello offline", assertIs<CreateMessageResult.Created>(result).message.text)
                assertEquals(
                    1L,
                    fixture.database.messagesQueries
                        .countMessages()
                        .executeAsOne(),
                )
                assertEquals(
                    1L,
                    fixture.database.messagesQueries
                        .countOutbox()
                        .executeAsOne(),
                )
            }
        }

    @Test
    fun `outbox failure rolls the message insert back`() =
        runTest {
            databaseFixture().use { fixture ->
                fixture.driver
                    .execute(
                        identifier = null,
                        sql =
                            """
                            CREATE TRIGGER reject_outbox
                            BEFORE INSERT ON message_outbox
                            BEGIN
                              SELECT RAISE(ABORT, 'outbox unavailable');
                            END
                            """.trimIndent(),
                        parameters = 0,
                    ).value
                val repository = fixture.repository(UnconfinedTestDispatcher(testScheduler))

                assertFailsWith<Throwable> {
                    repository.createOffline("must be atomic")
                }
                assertEquals(
                    0L,
                    fixture.database.messagesQueries
                        .countMessages()
                        .executeAsOne(),
                )
                assertEquals(
                    0L,
                    fixture.database.messagesQueries
                        .countOutbox()
                        .executeAsOne(),
                )
            }
        }

    @Test
    fun `observation emits after a durable local write`() =
        runTest {
            databaseFixture().use { fixture ->
                val repository = fixture.repository(UnconfinedTestDispatcher(testScheduler))
                val emissions = mutableListOf<List<Message>>()
                val collection =
                    backgroundScope.launch(
                        context = Dispatchers.Unconfined,
                        start = CoroutineStart.UNDISPATCHED,
                    ) {
                        repository.observeLocal().take(2).toList(emissions)
                    }

                repository.createOffline("reactive")

                withTimeout(5_000L) {
                    collection.join()
                }
                assertEquals(listOf(0, 1), emissions.map(List<Message>::size))
            }
        }

    @Test
    fun `messages survive closing and reopening the database`() =
        runTest {
            val path = Files.createTempFile("messages-restart-", ".db")
            try {
                val first = DatabaseFixture.create(path.toString())
                first.repository(UnconfinedTestDispatcher(testScheduler)).createOffline("survives restart")
                first.close()

                val second = DatabaseFixture.open(path.toString())
                assertEquals(
                    listOf("survives restart"),
                    second.repository(UnconfinedTestDispatcher(testScheduler)).listLocal().map(Message::text),
                )
                assertEquals(
                    1L,
                    second.database.messagesQueries
                        .countOutbox()
                        .executeAsOne(),
                )
                second.close()
            } finally {
                path.deleteIfExists()
            }
        }

    @Test
    fun `integer timeline produces deterministic chronological ordering`() =
        runTest {
            databaseFixture().use { fixture ->
                fixture.database.messagesQueries.insertPending("old", "old", 10L, 10L)
                fixture.database.messagesQueries.insertPending("new", "new", 20L, 20L)

                assertEquals(
                    listOf("new", "old"),
                    fixture.repository(UnconfinedTestDispatcher(testScheduler)).listLocal().map(Message::localId),
                )
            }
        }

    private fun databaseFixture(): DatabaseFixture {
        val path = Files.createTempFile("messages-", ".db")
        return DatabaseFixture.create(path.toString(), deleteOnClose = true)
    }
}

private class DatabaseFixture private constructor(
    val driver: JdbcSqliteDriver,
    val database: MessagesDatabase,
    private val path: String,
    private val deleteOnClose: Boolean,
) : AutoCloseable {
    fun repository(dispatcher: kotlinx.coroutines.CoroutineDispatcher): SqlDelightMessageRepository {
        val ids = ArrayDeque(listOf("local-id", "operation-id"))
        return SqlDelightMessageRepository(
            database = database,
            clock = Clock { 1_750_000_000_000L },
            uuidGenerator = UuidGenerator { ids.removeFirst() },
            queryDispatcher = dispatcher,
        )
    }

    override fun close() {
        driver.close()
        if (deleteOnClose) {
            Files.deleteIfExists(
                java.nio.file.Path
                    .of(path),
            )
        }
    }

    companion object {
        fun create(
            path: String,
            deleteOnClose: Boolean = false,
        ): DatabaseFixture {
            val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
            MessagesDatabase.Schema.create(driver).value
            return DatabaseFixture(driver, MessagesDatabase(driver), path, deleteOnClose)
        }

        fun open(path: String): DatabaseFixture {
            val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
            return DatabaseFixture(driver, MessagesDatabase(driver), path, deleteOnClose = false)
        }
    }
}
