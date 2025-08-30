package ai.koog.prompt.executor.clients.dashscope

import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.dashscope.models.DashscopeChatCompletionRequest
import ai.koog.prompt.executor.clients.dashscope.models.DashscopeChatCompletionResponse
import ai.koog.prompt.executor.clients.dashscope.models.DashscopeChatCompletionStreamResponse
import ai.koog.prompt.executor.clients.dashscope.models.DashscopeUsage
import ai.koog.prompt.executor.clients.openai.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIBasedSettings
import ai.koog.prompt.executor.clients.openai.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.models.OpenAIToolChoice
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.datetime.Clock

/**
 * Configuration settings for connecting to the DashScope API using OpenAI-compatible endpoints.
 *
 * @property baseUrl The base URL of the DashScope API. 
 * For international: "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
 * For China mainland: "https://dashscope.aliyuncs.com/compatible-mode/v1"
 * @property chatCompletionsPath The path for chat completions (default: "/chat/completions")
 * @property timeoutConfig Configuration for connection timeouts including request, connection, and socket timeouts.
 */
public class DashscopeClientSettings(
    baseUrl: String = "https://dashscope-intl.aliyuncs.com/",
    chatCompletionsPath: String = "compatible-mode/v1/chat/completions",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
) : OpenAIBasedSettings(baseUrl, chatCompletionsPath, timeoutConfig)

/**
 * Implementation of [AbstractOpenAILLMClient] for DashScope API using OpenAI-compatible endpoints.
 *
 * @param apiKey The API key for the DashScope API
 * @param settings The base URL, chat completion path, and timeouts for the DashScope API,
 * defaults to "https://dashscope-intl.aliyuncs.com/compatible-mode/v1" and 900s
 * @param baseClient HTTP client for making requests
 * @param clock Clock instance used for tracking response metadata timestamps
 */
public class DashscopeLLMClient(
    apiKey: String,
    private val settings: DashscopeClientSettings = DashscopeClientSettings(),
    baseClient: HttpClient = HttpClient(),
    clock: Clock = Clock.System
) : AbstractOpenAILLMClient<DashscopeChatCompletionResponse, DashscopeChatCompletionStreamResponse>(
    apiKey,
    settings,
    baseClient,
    clock
) {

    private companion object {
        private val staticLogger = KotlinLogging.logger { }
    }

    override val logger: KLogger = staticLogger

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean
    ): String {
        val dashscopeParams = params.toDashscopeParams()
        val responseFormat = createResponseFormat(params.schema, model)

        val request = DashscopeChatCompletionRequest(
            messages = messages,
            model = model.id,
            maxTokens = dashscopeParams.maxTokens,
            responseFormat = responseFormat,
            stream = stream,
            temperature = dashscopeParams.temperature,
            toolChoice = dashscopeParams.toolChoice?.toOpenAIToolChoice(),
            tools = tools?.takeIf { it.isNotEmpty() },
            topLogprobs = null,
            topP = null,
            frequencyPenalty = null,
            presencePenalty = null,
            stop = null
        )

        return json.encodeToString(request)
    }

    override fun processProviderChatResponse(response: DashscopeChatCompletionResponse): List<LLMChoice> {
        require(response.choices.isNotEmpty()) { "Empty choices in response" }
        return response.choices.map { it.toMessageResponses(response.usage.createMetaInfo()) }
    }

    override fun decodeStreamingResponse(data: String): DashscopeChatCompletionStreamResponse =
        json.decodeFromString(data)

    override fun decodeResponse(data: String): DashscopeChatCompletionResponse =
        json.decodeFromString(data)

    override fun processStreamingChunk(chunk: DashscopeChatCompletionStreamResponse): String? =
        chunk.choices.firstOrNull()?.delta?.content

    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by DashScope API" }
        throw UnsupportedOperationException("Moderation is not supported by DashScope API.")
    }

    /**
     * Creates ResponseMetaInfo from usage data.
     * Should be used by concrete implementations when processing responses.
     */
    private fun DashscopeUsage?.createMetaInfo(): ResponseMetaInfo = ResponseMetaInfo.create(
        clock,
        totalTokensCount = this?.totalTokens,
        inputTokensCount = this?.promptTokens,
        outputTokensCount = this?.completionTokens
    )
}
