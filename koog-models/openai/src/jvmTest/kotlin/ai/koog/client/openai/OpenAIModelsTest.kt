package ai.koog.client.openai

import ai.koog.client.list
import ai.koog.prompt.llm.LLMProvider
import kotlin.test.Test
import kotlin.test.assertSame

class OpenAIModelsTest {

    @Test
    fun `OpenAI models should have OpenAI provider`() {
        val models = OpenAIModels.list()

        models.forEach { model ->
            assertSame(
                expected = LLMProvider.OpenAI,
                actual = model.provider,
                message = "OpenAI model ${model.id} doesn't have OpenAI provider but ${model.provider}."
            )
        }
    }
}
