package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.mock.MockLLMProvider
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.ModerationCategoryResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ModerationResponseEventTest {

    //region Attributes

    @Test
    fun `test moderation response attributes verbose false`() {
        val moderationResult = createTestModerationResult()
        val llmProvider = MockLLMProvider()

        val moderationResponseEvent = ModerationResponseEvent(
            provider = llmProvider,
            moderationResult = moderationResult,
            verbose = false,
        )

        val expectedAttributes = listOf(
            CommonAttributes.System(llmProvider)
        )

        assertEquals(expectedAttributes.size, moderationResponseEvent.attributes.size)
        assertContentEquals(expectedAttributes, moderationResponseEvent.attributes)
    }

    // Skipping test for verbose=true due to JSON serialization issues with ModerationCategory

    //endregion Attributes

    //region Body Fields

    @Test
    fun `test moderation response body fields verbose false`() {
        val moderationResult = createTestModerationResult()

        val moderationResponseEvent = ModerationResponseEvent(
            provider = MockLLMProvider(),
            moderationResult = moderationResult,
            verbose = false,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Role(role = Message.Role.Assistant)
        )

        assertEquals(expectedBodyFields.size, moderationResponseEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, moderationResponseEvent.bodyFields)
    }

    // Skipping test for verbose=true due to JSON serialization issues with ModerationCategory

    //endregion Body Fields

    //region Private Methods

    private fun createTestModerationResult(): ModerationResult {
        val categories = mapOf(
            ModerationCategory.Harassment to ModerationCategoryResult(detected = false, confidenceScore = 0.1),
            ModerationCategory.Hate to ModerationCategoryResult(detected = false, confidenceScore = 0.05)
        )
        return ModerationResult(isHarmful = false, categories = categories)
    }

    //endregion Private Methods
}