package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.mock.MockLLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SystemMessageEventTest {

    //region Attributes

    @Test
    fun `test system message attributes`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestSystemMessage(expectedContent)
        val llmProvider = MockLLMProvider()

        val systemMessageEvent = SystemMessageEvent(
            provider = llmProvider,
            message = expectedMessage,
        )

        val expectedAttributes = listOf(
            CommonAttributes.System(llmProvider)
        )

        assertEquals(expectedAttributes.size, systemMessageEvent.attributes.size)
        assertContentEquals(expectedAttributes, systemMessageEvent.attributes)
    }

    //endregion Attributes

    //region Body Fields

    @Test
    fun `test system message body fields`() {
        val expectedMessage = createTestSystemMessage("Test message")

        val systemMessageEvent = SystemMessageEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Role(role = expectedMessage.role),
            EventBodyFields.Content(content = expectedMessage.content),
        )

        assertEquals(expectedBodyFields.size, systemMessageEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, systemMessageEvent.bodyFields)
    }

    //endregion Body Fields

    //region Private Methods

    private fun createTestSystemMessage(content: String): Message.System = Message.System(
        content = content,
        metaInfo = RequestMetaInfo(Clock.System.now())
    )

    //endregion Private Methods
}
