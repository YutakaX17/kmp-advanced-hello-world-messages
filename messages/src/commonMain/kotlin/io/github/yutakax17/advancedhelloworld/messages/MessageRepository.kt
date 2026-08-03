package io.github.yutakax17.advancedhelloworld.messages

import io.github.yutakax17.advancedhelloworld.core.SyncContributor

public interface MessageRepository {
    /**
     * Atomically saves a pending message and its durable CREATE outbox operation.
     */
    public suspend fun createOffline(text: String): CreateMessageResult

    public suspend fun listLocal(): List<Message>

    public val syncContributor: SyncContributor
}

public sealed interface CreateMessageResult {
    public data class Created(public val message: Message) : CreateMessageResult

    public data class Rejected(public val validation: MessageValidation) : CreateMessageResult
}
