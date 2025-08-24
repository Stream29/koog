package ai.koog.prompt.executor.clients.openai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.utils.SuitableForIO
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.models.Content
import ai.koog.prompt.executor.clients.openai.models.JsonSchemaObject
import ai.koog.prompt.executor.clients.openai.models.OpenAIBaseLLMResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIBaseLLMStreamResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIChoice
import ai.koog.prompt.executor.clients.openai.models.OpenAIContentPart
import ai.koog.prompt.executor.clients.openai.models.OpenAIFunction
import ai.koog.prompt.executor.clients.openai.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.models.OpenAIResponseFormat
import ai.koog.prompt.executor.clients.openai.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.models.OpenAIToolCall
import ai.koog.prompt.executor.clients.openai.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.models.OpenAIToolFunction
import ai.koog.prompt.executor.clients.openai.models.OpenAIUsage
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Base settings class for OpenAI-based API clients.
 *
 * @property baseUrl The base URL for the API endpoint.
 * @property chatCompletionsPath The path for chat completions API endpoints.
 * @property timeoutConfig Configuration for connection timeouts, including request, connect, and socket timeouts.
 */
public abstract class OpenAIBasedSettings(
    public val baseUrl: String,
    public val chatCompletionsPath: String,
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
)

/**
 * Abstract base class for OpenAI-compatible LLM clients.
 * Provides common functionality for communicating with OpenAI and OpenAI-compatible APIs.
 *
 * @param apiKey The API key for authentication with the OpenAI-compatible API.
 * @param settings Configuration settings including base URL, API paths, and timeout configuration.
 * @param baseClient The HTTP client to use for API requests. Defaults to a new HttpClient instance.
 * @param clock Clock instance used for tracking response metadata timestamps. Defaults to Clock.System.
 */
public abstract class AbstractOpenAILLMClient<TResponse : OpenAIBaseLLMResponse, TStreamResponse : OpenAIBaseLLMStreamResponse>(
    private val apiKey: String,
    settings: OpenAIBasedSettings,
    baseClient: HttpClient = HttpClient(),
    protected val clock: Clock = Clock.System
) : LLMClient {

    protected abstract val logger: KLogger

    protected open val clientName: String = this::class.simpleName ?: "UnknownClient"

    private val chatCompletionsPath: String = settings.chatCompletionsPath

    protected val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    protected val httpClient: HttpClient = baseClient.config {
        defaultRequest {
            url(settings.baseUrl)
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
        }
        install(SSE)
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = settings.timeoutConfig.requestTimeoutMillis // Increase timeout to 60 seconds
            connectTimeoutMillis = settings.timeoutConfig.connectTimeoutMillis
            socketTimeoutMillis = settings.timeoutConfig.socketTimeoutMillis
        }
    }

    /**
     * Creates a provider-specific request from the common parameters.
     * Must be implemented by concrete client classes.
     */
    protected abstract fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean,
    ): String

    /**
     * Processes a provider-specific response into a common message format.
     * Must be implemented by concrete client classes.
     */
    protected abstract fun processProviderChatResponse(response: TResponse): List<LLMChoice>

    /**
     * Decodes a streaming response from JSON string.
     * Must be implemented by concrete client classes.
     */
    protected abstract fun decodeStreamingResponse(data: String): TStreamResponse

    /**
     * Decodes a regular response from JSON string.
     * Must be implemented by concrete client classes.
     */
    protected abstract fun decodeResponse(data: String): TResponse

    /**
     * Processes a provider-specific streaming response chunk.
     * Must be implemented by concrete client classes.
     */
    protected abstract fun processStreamingChunk(chunk: TStreamResponse): String?

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        val response = getResponse(prompt, model, tools)
        return processProviderChatResponse(response).first()
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = flow {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        model.requireCapability(LLMCapability.Completion)

        val messages = convertPromptToMessages(prompt, model)
        val request = serializeProviderChatRequest(
            messages = messages,
            model = model,
            tools = emptyList(),
            toolChoice = prompt.params.toolChoice?.toOpenAIToolChoice(),
            params = prompt.params,
            stream = true
        )

        try {
            httpClient.sse(
                urlString = chatCompletionsPath,
                request = {
                    method = HttpMethod.Post
                    accept(ContentType.Text.EventStream)
                    headers {
                        append(HttpHeaders.CacheControl, "no-cache")
                        append(HttpHeaders.Connection, "keep-alive")
                    }
                    setBody(request)
                }
            ) {
                incoming.collect { event ->
                    event
                        .takeIf { it.data != "[DONE]" }
                        ?.data?.trim()
                        ?.let(::decodeStreamingResponse)
                        ?.let(::processStreamingChunk)
                        ?.let { emit(it) }
                }
            }
        } catch (e: SSEClientException) {
            e.response?.let { response ->
                val body = response.readRawBytes().decodeToString()
                logger.error(e) { "Error from $clientName API: ${response.status}: ${e.message}.\nBody:\n$body" }
                error("Error from $clientName API: ${response.status}: ${e.message}")
            }
        } catch (e: Exception) {
            logger.error { "Exception during streaming from $clientName: $e" }
            error(e.message ?: "Unknown error during streaming from $clientName: $e")
        }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> = processProviderChatResponse(getResponse(prompt, model, tools))

    private suspend fun getResponse(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): TResponse {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        model.requireCapability(LLMCapability.Completion)
        if (tools.isNotEmpty()) {
            model.requireCapability(LLMCapability.Tools)
        }

        val llmTools = tools.takeIf { it.isNotEmpty() }?.map { it.toOpenAIChatTool() }
        val messages = convertPromptToMessages(prompt, model)
        val request = serializeProviderChatRequest(
            messages = messages,
            model = model,
            tools = llmTools,
            toolChoice = prompt.params.toolChoice?.toOpenAIToolChoice(),
            params = prompt.params,
            stream = false
        )

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post(chatCompletionsPath) {
                setBody(request)
            }

            if (response.status.isSuccess()) {
                response.bodyAsText().let(::decodeResponse)
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from $clientName API: ${response.status}: $errorBody" }
                error("Error from $clientName API: ${response.status}: $errorBody")
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    protected fun convertPromptToMessages(prompt: Prompt, model: LLModel): List<OpenAIMessage> {
        val messages = mutableListOf<OpenAIMessage>()
        val pendingCalls = mutableListOf<OpenAIToolCall>()

        fun flushPendingCalls() {
            if (pendingCalls.isNotEmpty()) {
                messages += OpenAIMessage.Assistant(toolCalls = pendingCalls.toList())
                pendingCalls.clear()
            }
        }

        prompt.messages.forEach { message ->
            when (message) {
                is Message.System -> {
                    flushPendingCalls()
                    messages += OpenAIMessage.System(content = Content.Text(message.content))
                }

                is Message.User -> {
                    flushPendingCalls()
                    messages += OpenAIMessage.User(content = message.toMessageContent(model))
                }

                is Message.Assistant -> {
                    flushPendingCalls()
                    messages += OpenAIMessage.Assistant(content = Content.Text(message.content))
                }

                is Message.Tool.Result -> {
                    flushPendingCalls()
                    messages += OpenAIMessage.Tool(
                        content = Content.Text(message.content),
                        toolCallId = message.id ?: Uuid.random().toString()
                    )
                }

                is Message.Tool.Call -> {
                    pendingCalls += OpenAIToolCall(
                        message.id ?: Uuid.random().toString(),
                        function = OpenAIFunction(message.tool, message.content)
                    )
                }
            }
        }
        flushPendingCalls()

        return messages
    }

    protected fun Message.toMessageContent(model: LLModel): Content {
        if (this !is Message.WithAttachments || attachments.isEmpty()) {
            return Content.Text(content)
        }

        val parts = buildList {
            if (content.isNotEmpty()) {
                add(OpenAIContentPart.Text(content))
            }
            attachments.forEach { attachment -> add(attachment.toContentPart(model)) }
        }

        return Content.Parts(parts)
    }

    private fun Attachment.toContentPart(model: LLModel): OpenAIContentPart = when (this) {
        is Attachment.Image -> {
            model.requireCapability(LLMCapability.Vision.Image)
            val imageUrl = when (val attachmentContent = content) {
                is AttachmentContent.URL -> attachmentContent.url
                is AttachmentContent.Binary -> "data:$mimeType;base64,${attachmentContent.base64}"
                else -> throw IllegalArgumentException("Unsupported image attachment content: ${attachmentContent::class}")
            }
            OpenAIContentPart.Image(OpenAIContentPart.ImageUrl(imageUrl))
        }

        is Attachment.Audio -> {
            model.requireCapability(LLMCapability.Audio)
            val inputAudio = when (val attachmentContent = content) {
                is AttachmentContent.Binary -> OpenAIContentPart.InputAudio(attachmentContent.base64, format)
                else -> throw IllegalArgumentException("Unsupported audio attachment content: ${attachmentContent::class}")
            }
            OpenAIContentPart.Audio(inputAudio)
        }

        is Attachment.File -> {
            model.requireCapability(LLMCapability.Document)
            val fileData = when (val attachmentContent = content) {
                is AttachmentContent.Binary -> OpenAIContentPart.FileData(
                    fileData = "data:$mimeType;base64,${attachmentContent.base64}",
                    filename = fileName
                )

                else -> throw IllegalArgumentException("Unsupported file attachment content: ${attachmentContent::class}")
            }
            OpenAIContentPart.File(fileData)
        }

        else -> throw IllegalArgumentException("Unsupported attachment type: $this")
    }

    protected fun ToolDescriptor.toOpenAIChatTool(): OpenAITool = OpenAITool(
        function = OpenAIToolFunction(
            name = name,
            description = description,
            parameters = paramsToJsonObject()
        )
    )

    protected fun ToolDescriptor.paramsToJsonObject(): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                (requiredParameters + optionalParameters).forEach { param ->
                    put(param.name, param.toJsonSchema())
                }
            }
            putJsonArray("required") {
                requiredParameters.forEach { param -> add(param.name) }
            }
        }

    protected fun LLMParams.ToolChoice.toOpenAIToolChoice(): OpenAIToolChoice = when (this) {
        LLMParams.ToolChoice.Auto -> OpenAIToolChoice.Auto
        LLMParams.ToolChoice.None -> OpenAIToolChoice.None
        LLMParams.ToolChoice.Required -> OpenAIToolChoice.Required
        is LLMParams.ToolChoice.Named -> OpenAIToolChoice.Function(
            function = OpenAIToolChoice.FunctionName(name)
        )
    }

    protected fun ToolParameterDescriptor.toJsonSchema(): JsonObject = buildJsonObject {
        put("description", description)
        fillJsonSchema(type)
    }

    private fun JsonObjectBuilder.fillJsonSchema(type: ToolParameterType) {
        when (type) {
            ToolParameterType.Boolean -> put("type", "boolean")
            ToolParameterType.Float -> put("type", "number")
            ToolParameterType.Integer -> put("type", "integer")
            ToolParameterType.String -> put("type", "string")
            is ToolParameterType.Enum -> {
                put("type", "string")
                putJsonArray("enum") {
                    type.entries.forEach { entry -> add(entry) }
                }
            }

            is ToolParameterType.List -> {
                put("type", "array")
                putJsonObject("items") { fillJsonSchema(type.itemsType) }
            }

            is ToolParameterType.Object -> {
                put("type", "object")
                type.additionalProperties?.let { put("additionalProperties", it) }
                putJsonObject("properties") {
                    type.properties.forEach { property ->
                        putJsonObject(property.name) {
                            fillJsonSchema(property.type)
                            put("description", property.description)
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    protected fun OpenAIChoice.toMessageResponses(metaInfo: ResponseMetaInfo): List<Message.Response> {
        return when {
            message is OpenAIMessage.Assistant && !message.toolCalls.isNullOrEmpty() -> {
                message.toolCalls.map { toolCall ->
                    Message.Tool.Call(
                        id = toolCall.id,
                        tool = toolCall.function.name,
                        content = toolCall.function.arguments,
                        metaInfo = metaInfo
                    )
                }
            }

            message.content != null -> listOf(
                Message.Assistant(
                    content = message.content!!.text(),
                    finishReason = finishReason,
                    metaInfo = metaInfo
                )
            )

            message is OpenAIMessage.Assistant && message.audio?.data != null -> listOf(
                Message.Assistant(
                    content = message.audio.transcript.orEmpty(),
                    attachments = listOf(
                        Attachment.Audio(
                            content = AttachmentContent.Binary.Base64(message.audio.data),
                            format = "unknown", // FIXME: clarify format from response
                        )
                    ),
                    finishReason = finishReason,
                    metaInfo = metaInfo
                )
            )

            else -> {
                logger.error { "Unexpected response from $clientName: no tool calls and no content" }
                error("Unexpected response from $clientName: no tool calls and no content")
            }
        }
    }

    protected fun LLModel.requireCapability(capability: LLMCapability) {
        require(supports(capability)) { "Model $id does not support ${capability.id}" }
    }

    /**
     * Creates ResponseMetaInfo from usage data.
     * Should be used by concrete implementations when processing responses.
     */
    protected fun createMetaInfo(usage: OpenAIUsage?): ResponseMetaInfo = ResponseMetaInfo.create(
        clock,
        totalTokensCount = usage?.totalTokens,
        inputTokensCount = usage?.promptTokens,
        outputTokensCount = usage?.completionTokens
    )

    protected fun createResponseFormat(schema: LLMParams.Schema?, model: LLModel): OpenAIResponseFormat? {
        return schema?.let {
            require(it.capability in model.capabilities) {
                "Model ${model.id} does not support structured output schema ${it.name}"
            }
            when (it) {
                is LLMParams.Schema.JSON -> OpenAIResponseFormat.JsonSchema(
                    JsonSchemaObject(
                        name = it.name,
                        schema = it.schema,
                        strict = true
                    )
                )
            }
        }
    }
}
