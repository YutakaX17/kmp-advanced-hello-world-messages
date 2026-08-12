package io.github.yutakax17.advancedhelloworld.messages

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.yutakax17.advancedhelloworld.core.AppFailure
import io.github.yutakax17.advancedhelloworld.core.Clock
import io.github.yutakax17.advancedhelloworld.core.SyncResult
import io.github.yutakax17.advancedhelloworld.core.UuidGenerator
import io.github.yutakax17.advancedhelloworld.messages.database.MessagesDatabase
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageSynchronizationEngineTest {
    @Test
    fun `offline upload retries with the same durable idempotency key then succeeds`() =
        runTest {
            synchronizationFixture().use { fixture ->
            fixture.repository.createOffline("recover me")
            fixture.remote.createResults += RemoteResult.Failure(AppFailure.Connectivity("offline"))
            fixture.remote.createResults += RemoteResult.Success(remoteMessage("remote-1", "recover me"))

            assertIs<SyncResult.Retry>(fixture.engine.synchronize())
            fixture.now = 10_000L
            assertEquals(SyncResult.Success, fixture.engine.synchronize())

            assertEquals(listOf("operation-id", "operation-id"), fixture.remote.idempotencyKeys)
            assertEquals(0L, fixture.database.messagesQueries.countOutbox().executeAsOne())
            assertEquals(MessageSyncState.SYNCED, fixture.repository.listLocal().single().syncState)
            }
        }

    @Test
    fun `paginated pull saves cursor and reconciles duplicate remote delivery`() =
        runTest {
            synchronizationFixture().use { fixture ->
            fixture.remote.pages[null] =
                RemoteResult.Success(
                    RemoteMessagePage(listOf(remoteMessage("remote-1", "first")), "page-2"),
                )
            fixture.remote.pages["page-2"] =
                RemoteResult.Success(
                    RemoteMessagePage(listOf(remoteMessage("remote-1", "updated")), null),
                )

            assertEquals(SyncResult.Success, fixture.engine.synchronize())

            assertEquals(listOf(null, "page-2"), fixture.remote.requestedCursors)
            assertEquals(1L, fixture.database.messagesQueries.countMessages().executeAsOne())
            assertEquals("updated", fixture.repository.listLocal().single().text)
            assertEquals(null, fixture.database.messagesQueries.getSyncMetadata().executeAsOne().pull_cursor)
            }
        }

    @Test
    fun `failed later page resumes from durable cursor`() =
        runTest {
            synchronizationFixture().use { fixture ->
            fixture.remote.pages[null] =
                RemoteResult.Success(
                    RemoteMessagePage(listOf(remoteMessage("remote-1", "first")), "page-2"),
                )
            fixture.remote.pages["page-2"] = RemoteResult.Failure(AppFailure.Connectivity("offline"))

            assertIs<SyncResult.Retry>(fixture.engine.synchronize())
            assertEquals("page-2", fixture.database.messagesQueries.getSyncMetadata().executeAsOne().pull_cursor)

            fixture.remote.pages["page-2"] =
                RemoteResult.Success(
                    RemoteMessagePage(listOf(remoteMessage("remote-2", "second")), null),
                )
            assertEquals(SyncResult.Success, fixture.engine.synchronize())

            assertEquals(listOf(null, "page-2", "page-2"), fixture.remote.requestedCursors)
            assertEquals(2L, fixture.database.messagesQueries.countMessages().executeAsOne())
            }
        }

    @Test
    fun `permanent upload failure marks message failed and removes outbox operation`() =
        runTest {
            synchronizationFixture().use { fixture ->
            fixture.repository.createOffline("invalid remotely")
            fixture.remote.createResults +=
                RemoteResult.Failure(AppFailure.Remote(400, "Text was rejected"))

            assertIs<SyncResult.PermanentFailure>(fixture.engine.synchronize())

            assertEquals(MessageSyncState.FAILED_PERMANENT, fixture.repository.listLocal().single().syncState)
            assertEquals(0L, fixture.database.messagesQueries.countOutbox().executeAsOne())
            }
        }
}

private class FakeMessageRemoteDataSource : MessageRemoteDataSource {
    val createResults = ArrayDeque<RemoteResult<RemoteMessage>>()
    val pages = mutableMapOf<String?, RemoteResult<RemoteMessagePage>>()
    val idempotencyKeys = mutableListOf<String>()
    val requestedCursors = mutableListOf<String?>()

    override suspend fun createMessage(
        request: CreateRemoteMessage,
        idempotencyKey: String,
    ): RemoteResult<RemoteMessage> {
        idempotencyKeys += idempotencyKey
        return createResults.removeFirst()
    }

    override suspend fun listMessages(cursor: String?): RemoteResult<RemoteMessagePage> {
        requestedCursors += cursor
        return pages[cursor] ?: RemoteResult.Success(RemoteMessagePage(emptyList(), null))
    }
}

private class SynchronizationFixture(
    private val path: java.nio.file.Path,
    private val driver: JdbcSqliteDriver,
    val database: MessagesDatabase,
    val remote: FakeMessageRemoteDataSource,
    val repository: SqlDelightMessageRepository,
    var now: Long,
) : AutoCloseable {
    val engine =
        MessageSynchronizationEngine(
            database = database,
            remote = remote,
            clock = Clock { now },
            uuidGenerator = UuidGenerator { "pulled-${database.messagesQueries.countMessages().executeAsOne()}" },
            retryDelayMilliseconds = { 5_000L },
        )

    override fun close() {
        driver.close()
        Files.deleteIfExists(path)
    }
}

private fun synchronizationFixture(): SynchronizationFixture {
    val path = Files.createTempFile("message-sync-", ".db")
    val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
    MessagesDatabase.Schema.create(driver).value
    val database = MessagesDatabase(driver)
    var now = 0L
    val ids = ArrayDeque(listOf("local-id", "operation-id"))
    val repository =
        SqlDelightMessageRepository(
            database = database,
            clock = Clock { now },
            uuidGenerator = UuidGenerator { ids.removeFirst() },
            queryDispatcher = UnconfinedTestDispatcher(),
        )
    return SynchronizationFixture(path, driver, database, FakeMessageRemoteDataSource(), repository, now)
}

private fun remoteMessage(id: String, text: String): RemoteMessage =
    RemoteMessage(id = id, text = text, createdAt = "2026-08-12T08:00:00Z")
