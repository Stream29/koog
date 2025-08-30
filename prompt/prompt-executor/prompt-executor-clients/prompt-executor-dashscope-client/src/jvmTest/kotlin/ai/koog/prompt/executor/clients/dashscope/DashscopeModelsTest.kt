package ai.koog.prompt.executor.clients.dashscope

import ai.koog.prompt.llm.LLMCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DashscopeModelsTest {

    @Test
    fun `QWEN_TURBO should have correct properties`() {
        val model = DashscopeModels.QWEN_FLASH
        assertEquals("qwen-turbo", model.id)
        assertEquals(128_000, model.contextLength)
        assertEquals(6144, model.maxOutputTokens)
        assertTrue(model.capabilities.contains(LLMCapability.Completion))
        assertTrue(model.capabilities.contains(LLMCapability.Tools))
        assertTrue(model.capabilities.contains(LLMCapability.Temperature))
    }

    @Test
    fun `QWEN_PLUS should have correct properties`() {
        val model = DashscopeModels.QWEN_PLUS
        assertEquals("qwen-plus", model.id)
        assertEquals(128_000, model.contextLength)
        assertEquals(6144, model.maxOutputTokens)
        assertTrue(model.capabilities.contains(LLMCapability.Completion))
        assertTrue(model.capabilities.contains(LLMCapability.Tools))
        assertTrue(model.capabilities.contains(LLMCapability.Temperature))
    }
}
