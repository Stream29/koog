package ai.koog.prompt.executor.clients.dashscope

import ai.koog.prompt.params.LLMParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DashscopeParamsTest {

    @Test
    fun `DashscopeParams should have correct default values`() {
        val params = DashscopeParams()
        assertNull(params.temperature)
        assertNull(params.maxTokens)
        assertNull(params.toolChoice)
    }

    @Test
    fun `DashscopeParams should accept all parameters`() {
        val toolChoice = LLMParams.ToolChoice.Auto
        val params = DashscopeParams(
            temperature = 0.7,
            maxTokens = 1000,
            toolChoice = toolChoice
        )
        
        assertEquals(0.7, params.temperature)
        assertEquals(1000, params.maxTokens)
        assertEquals(toolChoice, params.toolChoice)
    }

    @Test
    fun `toDashscopeParams should convert LLMParams correctly`() {
        val llmParams = LLMParams(
            temperature = 0.8,
            maxTokens = 2000,
            toolChoice = LLMParams.ToolChoice.Named("test_tool")
        )
        
        val dashscopeParams = llmParams.toDashscopeParams()
        
        assertEquals(0.8, dashscopeParams.temperature)
        assertEquals(2000, dashscopeParams.maxTokens)
        assertEquals(LLMParams.ToolChoice.Named("test_tool"), dashscopeParams.toolChoice)
    }

    @Test
    fun `toDashscopeParams should handle null values`() {
        val llmParams = LLMParams()
        val dashscopeParams = llmParams.toDashscopeParams()
        
        assertNull(dashscopeParams.temperature)
        assertNull(dashscopeParams.maxTokens)
        assertNull(dashscopeParams.toolChoice)
    }
}