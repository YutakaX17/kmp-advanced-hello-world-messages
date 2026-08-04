import io.github.yutakax17.advancedhelloworld.messages.MessageValidation
import io.github.yutakax17.advancedhelloworld.messages.validateMessageText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PublishedMessagesTest {
    @Test
    fun `published API is usable from a clean build`() {
        val valid = assertIs<MessageValidation.Valid>(validateMessageText(" published "))
        assertEquals("published", valid.normalizedText)
    }
}
