package io.github.yutakax17.advancedhelloworld.messages

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorMessageRemoteDataSourceTest {
    @Test
    fun `post matches Django contract and sends durable idempotency key`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("https://example.test/api/v1/messages", request.url.toString())
            assertEquals("operation-1", request.headers[KtorMessageRemoteDataSource.IDEMPOTENCY_KEY_HEADER])
            respond(
                content = """{"id":"remote-1","text":"hello","createdAt":"2026-08-12T08:00:00Z"}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val remote = KtorMessageRemoteDataSource(testClient(engine), "https://example.test/")

        val result = remote.createMessage(CreateRemoteMessage("hello"), "operation-1")

        assertEquals("remote-1", assertIs<RemoteResult.Success<RemoteMessage>>(result).value.id)
    }

    @Test
    fun `get adapts current full list response into a terminal page`() = runTest {
        val engine = MockEngine {
            respond(
                content =
                    """[{"id":"remote-2","text":"newest","createdAt":"2026-08-12T09:00:00Z"},""" +
                        """{"id":"remote-1","text":"oldest","createdAt":"2026-08-12T08:00:00Z"}]""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val remote = KtorMessageRemoteDataSource(testClient(engine), "https://example.test")

        val page = assertIs<RemoteResult.Success<RemoteMessagePage>>(remote.listMessages(null)).value

        assertEquals(listOf("newest", "oldest"), page.messages.map(RemoteMessage::text))
        assertEquals(null, page.nextCursor)
    }

    @Test
    fun `client error is classified as permanent remote failure`() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.BadRequest) }
        val remote = KtorMessageRemoteDataSource(testClient(engine), "https://example.test")

        val failure =
            assertIs<RemoteResult.Failure>(
                remote.createMessage(CreateRemoteMessage("invalid"), "operation-1"),
            ).failure

        assertEquals(
            io.github.yutakax17.advancedhelloworld.core.RetryDirective.DO_NOT_RETRY,
            failure.retryDirective,
        )
    }
}

private fun testClient(engine: MockEngine): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
