package io.github.yutakax17.advancedhelloworld.messages

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.yutakax17.advancedhelloworld.core.Clock
import io.github.yutakax17.advancedhelloworld.core.SyncContributor
import io.github.yutakax17.advancedhelloworld.core.SyncResult
import io.github.yutakax17.advancedhelloworld.core.UuidGenerator
import io.github.yutakax17.advancedhelloworld.messages.database.MessagesDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Durable local-first repository. The platform owns the SQLDelight driver and
 * therefore controls the database file and lifecycle.
 */
public class SqlDelightMessageRepository(
    private val database: MessagesDatabase,
    private val clock: Clock,
    private val uuidGenerator: UuidGenerator,
    private val queryDispatcher: CoroutineDispatcher,
    override val syncContributor: SyncContributor =
        SyncContributor {
            SyncResult.Retry("Backend message synchronization is not configured")
        },
) : MessageRepository {
    override suspend fun createOffline(text: String): CreateMessageResult {
        val validation = validateMessageText(text)
        if (validation !is MessageValidation.Valid) {
            return CreateMessageResult.Rejected(validation)
        }

        val createdAt = clock.nowEpochMilliseconds()
        val localId = uuidGenerator.randomUuid()
        val operationId = uuidGenerator.randomUuid()
        val message =
            Message(
                localId = localId,
                remoteId = null,
                text = validation.normalizedText,
                createdAtLocal = createdAt,
                createdAtServer = null,
                syncState = MessageSyncState.PENDING,
            )
        val payload =
            buildJsonObject {
                put("localId", localId)
                put("text", validation.normalizedText)
                put("createdAtLocal", createdAt)
            }.toString()

        database.transaction {
            database.messagesQueries.insertPending(
                local_id = localId,
                text = validation.normalizedText,
                created_at_local = createdAt,
                timeline_at = createdAt,
            )
            database.messagesQueries.enqueueCreate(
                operation_id = operationId,
                entity_local_id = localId,
                payload = payload,
                created_at = createdAt,
            )
        }
        return CreateMessageResult.Created(message)
    }

    override fun observeLocal(): Flow<List<Message>> =
        database.messagesQueries
            .selectAll(::mapMessage)
            .asFlow()
            .mapToList(queryDispatcher)

    override suspend fun listLocal(): List<Message> = database.messagesQueries.selectAll(::mapMessage).executeAsList()

    private fun mapMessage(
        localId: String,
        remoteId: String?,
        text: String,
        createdAtLocal: Long,
        createdAtServer: String?,
        syncState: String,
        @Suppress("UNUSED_PARAMETER") attemptCount: Long,
        @Suppress("UNUSED_PARAMETER") lastError: String?,
        @Suppress("UNUSED_PARAMETER") timelineAt: Long,
    ): Message =
        Message(
            localId = localId,
            remoteId = remoteId,
            text = text,
            createdAtLocal = createdAtLocal,
            createdAtServer = createdAtServer,
            syncState = MessageSyncState.valueOf(syncState),
        )
}
