package io.github.yutakax17.advancedhelloworld.messages

public const val MAX_MESSAGE_LENGTH: Int = 500

public enum class MessageSyncState {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED_PERMANENT,
}

public data class Message(
    public val localId: String,
    public val remoteId: String?,
    public val text: String,
    public val createdAtLocal: Long,
    public val createdAtServer: String?,
    public val syncState: MessageSyncState,
)

public sealed interface MessageValidation {
    public data class Valid(public val normalizedText: String) : MessageValidation

    public data object Blank : MessageValidation

    public data class TooLong(public val maximumLength: Int) : MessageValidation
}

public fun validateMessageText(text: String): MessageValidation {
    val normalized = text.trim()
    return when {
        normalized.isEmpty() -> MessageValidation.Blank
        normalized.length > MAX_MESSAGE_LENGTH -> MessageValidation.TooLong(MAX_MESSAGE_LENGTH)
        else -> MessageValidation.Valid(normalized)
    }
}
