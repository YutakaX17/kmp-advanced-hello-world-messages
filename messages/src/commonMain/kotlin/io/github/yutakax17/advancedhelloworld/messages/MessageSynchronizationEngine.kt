package io.github.yutakax17.advancedhelloworld.messages

import io.github.yutakax17.advancedhelloworld.core.AppFailure
import io.github.yutakax17.advancedhelloworld.core.Clock
import io.github.yutakax17.advancedhelloworld.core.RetryDirective
import io.github.yutakax17.advancedhelloworld.core.SyncContributor
import io.github.yutakax17.advancedhelloworld.core.SyncResult
import io.github.yutakax17.advancedhelloworld.core.UuidGenerator
import io.github.yutakax17.advancedhelloworld.messages.database.MessagesDatabase
import kotlin.time.Instant

public class MessageSynchronizationEngine(
    private val database: MessagesDatabase,
    private val remote: MessageRemoteDataSource,
    private val clock: Clock,
    private val uuidGenerator: UuidGenerator,
    private val retryDelayMilliseconds: (Long) -> Long = ::defaultRetryDelayMilliseconds,
) : SyncContributor {
    override suspend fun synchronize(): SyncResult {
        val pushResult = pushReadyOutbox()
        if (pushResult != SyncResult.Success) return pushResult
        return pullRemoteMessages()
    }

    @Suppress("ReturnCount")
    private suspend fun pushReadyOutbox(): SyncResult {
        val now = clock.nowEpochMilliseconds()
        val operations = database.messagesQueries.selectReadyOutbox(now).executeAsList()
        for (operation in operations) {
            val message = database.messagesQueries.selectMessageByLocalId(operation.entity_local_id).executeAsOne()
            database.messagesQueries.markSyncing(operation.entity_local_id)
            when (
                val result =
                    remote.createMessage(
                        request = CreateRemoteMessage(message.text),
                        idempotencyKey = operation.operation_id,
                    )
            ) {
                is RemoteResult.Success -> {
                    val timeline =
                        result.value.createdAt.toEpochMillisecondsOrNull()
                            ?: return handleFailure(
                                operation.operation_id,
                                operation.entity_local_id,
                                operation.attempt_count,
                                AppFailure.InvalidData("Message backend returned an invalid createdAt value"),
                            )
                    database.transaction {
                        database.messagesQueries.markSynced(
                            remote_id = result.value.id,
                            created_at_server = result.value.createdAt,
                            timeline_at = timeline,
                            local_id = operation.entity_local_id,
                        )
                        database.messagesQueries.deleteOutboxOperation(operation.operation_id)
                    }
                }

                is RemoteResult.Failure -> {
                    return handleFailure(
                        operation.operation_id,
                        operation.entity_local_id,
                        operation.attempt_count,
                        result.failure,
                    )
                }
            }
        }
        return SyncResult.Success
    }

    @Suppress("ReturnCount")
    private suspend fun pullRemoteMessages(): SyncResult {
        var cursor =
            database.messagesQueries
                .getSyncMetadata()
                .executeAsOneOrNull()
                ?.pull_cursor
        do {
            when (val result = remote.listMessages(cursor)) {
                is RemoteResult.Failure -> {
                    return result.failure.toSyncResult()
                }

                is RemoteResult.Success -> {
                    val page = result.value
                    val mapped =
                        page.messages.map { remoteMessage ->
                            remoteMessage to
                                (
                                    remoteMessage.createdAt.toEpochMillisecondsOrNull()
                                        ?: return SyncResult.PermanentFailure(
                                            "Message backend returned invalid createdAt data",
                                        )
                                )
                        }
                    database.transaction {
                        mapped.forEach { (message, timeline) ->
                            database.messagesQueries.insertRemoteIfAbsent(
                                local_id = uuidGenerator.randomUuid(),
                                remote_id = message.id,
                                text = message.text,
                                created_at_local = timeline,
                                created_at_server = message.createdAt,
                                timeline_at = timeline,
                            )
                            database.messagesQueries.updateRemote(
                                text = message.text,
                                created_at_server = message.createdAt,
                                timeline_at = timeline,
                                remote_id = message.id,
                            )
                        }
                        database.messagesQueries.saveCursor(
                            pull_cursor = page.nextCursor,
                            last_successful_sync_at = clock.nowEpochMilliseconds(),
                        )
                    }
                    cursor = page.nextCursor
                }
            }
        } while (cursor != null)
        return SyncResult.Success
    }

    private fun handleFailure(
        operationId: String,
        localId: String,
        attemptCount: Long,
        failure: AppFailure,
    ): SyncResult {
        val reason = failure.message
        return if (failure.retryDirective == RetryDirective.DO_NOT_RETRY) {
            database.transaction {
                database.messagesQueries.markPermanentFailure(reason, localId)
                database.messagesQueries.deleteOutboxOperation(operationId)
            }
            SyncResult.PermanentFailure(reason)
        } else {
            val nextAttemptAt = clock.nowEpochMilliseconds() + retryDelayMilliseconds(attemptCount + 1L)
            database.transaction {
                database.messagesQueries.recordRetry(nextAttemptAt, operationId)
                database.messagesQueries.markPending(reason, localId)
            }
            SyncResult.Retry(reason)
        }
    }
}

public fun defaultRetryDelayMilliseconds(attempt: Long): Long {
    val exponent = (attempt - 1L).coerceIn(0L, 6L).toInt()
    return 5_000L * (1L shl exponent)
}

private fun String.toEpochMillisecondsOrNull(): Long? =
    try {
        Instant.parse(this).toEpochMilliseconds()
    } catch (_: IllegalArgumentException) {
        null
    }

private fun AppFailure.toSyncResult(): SyncResult =
    if (retryDirective == RetryDirective.DO_NOT_RETRY) {
        SyncResult.PermanentFailure(message)
    } else {
        SyncResult.Retry(message)
    }
