package ai.koog.client

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.flow.Flow

/**
 * Common interface for direct communication with LLM providers.
 * This interface defines methods for executing prompts and streaming responses.
 */
public interface LLMClient {
    /**
     * Executes a prompt and returns a list of response messages.
     *
     * @param prompt The prompt to execute
     * @param model The LLM model to use
     * @param tools Optional list of tools that can be used by the LLM
     * @return List of response messages
     */
    public suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList()
    ): List<Message.Response>

    /**
     * Executes a prompt and returns a streaming flow of response chunks.
     *
     * @param prompt The prompt to execute
     * @param model The LLM model to use
     * @return Flow of response chunks
     */
    public fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String>

    /**
     * Executes a prompt and returns a list of LLM choices.
     *
     * @param prompt The prompt to execute
     * @param tools Optional list of tools that can be used by the LLM
     * @param model The LLM model to use
     *  @return List of LLM choices
     */
    public suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> =
        throw UnsupportedOperationException("Not implemented for this client")

    /**
     * Analyzes the provided prompt for violations of content policies or other moderation criteria.
     *
     * @param prompt The input prompt to be analyzed for moderation.
     * @param model The language model to be used for conducting the moderation analysis.
     * @return The result of the moderation analysis, encapsulated in a ModerationResult object.
     */
    public suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult
}

/**
 * ConnectionTimeoutConfig is a configuration class for specifying timeout values
 * for network connections in milliseconds. It includes configurable timeouts for
 * requests, connection establishment, and socket operations.
 *
 * @property requestTimeoutMillis The maximum amount of time, in milliseconds, allowed for a request to complete
 *                                before timing out. Defaults to 900 seconds.
 * @property connectTimeoutMillis The maximum amount of time, in milliseconds, allowed for establishing a connection
 *                                to the server. Defaults to 60 seconds.
 * @property socketTimeoutMillis  The maximum amount of time, in milliseconds, allowed for waiting for data over
 *                                an established socket connection. Defaults to 900 seconds.
 */
public data class ConnectionTimeoutConfig(
    val requestTimeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    val socketTimeoutMillis: Long = DEFAULT_TIMEOUT_MS,
) {
    private companion object {
        private const val DEFAULT_TIMEOUT_MS: Long = 900000 // 900 seconds
        private const val DEFAULT_CONNECT_TIMEOUT_MS: Long = 60_000
    }
}
