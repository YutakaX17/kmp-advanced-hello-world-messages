package io.github.yutakax17.advancedhelloworld.messages

import io.github.yutakax17.advancedhelloworld.core.AppFailure
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
public data class RemoteMessage(
    public val id: String,
    public val text: String,
    public val createdAt: String,
)

@Serializable
public data class CreateRemoteMessage(
    public val text: String,
)

public data class RemoteMessagePage(
    public val messages: List<RemoteMessage>,
    public val nextCursor: String?,
)

public sealed interface RemoteResult<out T> {
    public data class Success<T>(
        public val value: T,
    ) : RemoteResult<T>

    public data class Failure(
        public val failure: AppFailure,
    ) : RemoteResult<Nothing>
}

public interface MessageRemoteDataSource {
    public suspend fun createMessage(
        request: CreateRemoteMessage,
        idempotencyKey: String,
    ): RemoteResult<RemoteMessage>

    public suspend fun listMessages(cursor: String?): RemoteResult<RemoteMessagePage>
}

/** Ktor adapter for the current backend's full-list `/api/v1/messages` contract. */
public class KtorMessageRemoteDataSource(
    private val client: HttpClient,
    baseUrl: String,
) : MessageRemoteDataSource {
    private val endpoint: String = "${baseUrl.trimEnd('/')}/api/v1/messages"

    override suspend fun createMessage(
        request: CreateRemoteMessage,
        idempotencyKey: String,
    ): RemoteResult<RemoteMessage> =
        requestRemote {
            client
                .post(endpoint) {
                    expectSuccess = true
                    contentType(ContentType.Application.Json)
                    header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                    setBody(request)
                }.body()
        }

    override suspend fun listMessages(cursor: String?): RemoteResult<RemoteMessagePage> =
        requestRemote {
            // The current backend returns one complete newest-first list. The cursor is
            // retained in the public boundary for a future incremental backend contract.
            val messages: List<RemoteMessage> = client.get(endpoint) { expectSuccess = true }.body()
            RemoteMessagePage(messages = messages, nextCursor = null)
        }

    private suspend fun <T> requestRemote(block: suspend () -> T): RemoteResult<T> =
        try {
            RemoteResult.Success(block())
        } catch (failure: io.ktor.client.plugins.ResponseException) {
            RemoteResult.Failure(
                AppFailure.Remote(failure.response.status.value, "Message backend request failed"),
            )
        } catch (failure: kotlinx.serialization.SerializationException) {
            RemoteResult.Failure(AppFailure.InvalidData("Message backend returned invalid data"))
        } catch (failure: Throwable) {
            RemoteResult.Failure(AppFailure.Connectivity(failure.message ?: "Message backend is unavailable"))
        }

    public companion object {
        public const val IDEMPOTENCY_KEY_HEADER: String = "Idempotency-Key"
    }
}
