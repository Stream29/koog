package ai.koog.prompt.executor.clients.dashscope

import ai.koog.prompt.params.LLMParams

/**
 * DashScope-specific parameters for fine-tuning the model's behavior.
 * These parameters are compatible with OpenAI API format.
 *
 * @property temperature Controls randomness: lower values for more focused, higher values for more creative outputs.
 * @property maxTokens The maximum number of tokens to generate.
 * @property toolChoice Controls which (if any) tool is called by the model.
 */
public data class DashscopeParams(
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val toolChoice: LLMParams.ToolChoice? = null
)

internal fun LLMParams.toDashscopeParams(): DashscopeParams {
    return DashscopeParams(
        temperature = this.temperature,
        maxTokens = this.maxTokens,
        toolChoice = this.toolChoice
    )
}
