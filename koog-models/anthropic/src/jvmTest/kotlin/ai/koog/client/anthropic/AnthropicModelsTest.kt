package ai.koog.client.anthropic

import ai.koog.client.list
import ai.koog.prompt.llm.LLMProvider
import kotlin.test.Test
import kotlin.test.assertSame

class AnthropicModelsTest {

    @Test
    fun `Anthropic models should have Anthropic provider`() {
        val models = AnthropicModels.list()

        models.forEach { model ->
            assertSame(
                expected = LLMProvider.Anthropic,
                actual = model.provider,
                message = "Anthropic model ${model.id} doesn't have Anthropic provider but ${model.provider}."
            )
        }
    }
}
