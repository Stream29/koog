package ai.koog.client.google

import ai.koog.client.list
import ai.koog.prompt.llm.LLMProvider
import kotlin.test.Test
import kotlin.test.assertSame

class GoogleModelsTest {

    @Test
    fun `Google models should have Google provider`() {
        val models = GoogleModels.list()

        models.forEach { model ->
            assertSame(
                expected = LLMProvider.Google,
                actual = model.provider,
                message = "Google model ${model.id} doesn't have Google provider but ${model.provider}."
            )
        }
    }
}
