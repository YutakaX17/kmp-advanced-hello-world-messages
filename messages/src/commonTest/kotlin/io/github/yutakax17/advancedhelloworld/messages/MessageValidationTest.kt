package io.github.yutakax17.advancedhelloworld.messages

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageValidationTest {
    @Test
    fun trimsValidText() {
        assertEquals(
            MessageValidation.Valid("Hello"),
            validateMessageText("  Hello  "),
        )
    }

    @Test
    fun rejectsBlankText() {
        assertIs<MessageValidation.Blank>(validateMessageText(" \n "))
    }

    @Test
    fun rejectsTextLongerThanBackendLimit() {
        assertEquals(
            MessageValidation.TooLong(MAX_MESSAGE_LENGTH),
            validateMessageText("x".repeat(MAX_MESSAGE_LENGTH + 1)),
        )
    }
}
