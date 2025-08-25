package ai.koog.prompt.executor.clients.google

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.utils.SuitableForIO
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.google.structure.GoogleBasicJsonSchemaGenerator
import ai.koog.prompt.executor.clients.google.structure.GoogleResponseFormat
import ai.koog.prompt.executor.clients.google.structure.GoogleStandardJsonSchemaGenerator
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.RegisteredBasicJsonSchemaGenerators
import ai.koog.prompt.structure.RegisteredStandardJsonSchemaGenerators
import ai.koog.prompt.structure.annotations.InternalStructuredOutputApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.accept
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Configuration settings for the Google AI client.
 *
 * @property baseUrl The base URL for the Google AI API.
 * @property timeoutConfig Timeout configuration for API requests.
 */
public class GoogleClientSettings(
    public val baseUrl: String = "https://generativelanguage.googleapis.com",
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
)

/**
 * Implementation of [LLMClient] for Google's Gemini API.
 *
 * This client supports both standard and streaming text generation with
 * optional tool calling capabilities.
 *
 * @param apiKey The API key for the Google AI API
 * @param settings Custom client settings, defaults to standard API endpoint and timeouts
 * @param baseClient Optional custom HTTP client
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
public open class GoogleLLMClient(
    private val apiKey: String,
    private val settings: GoogleClientSettings = GoogleClientSettings(),
    baseClient: HttpClient = HttpClient(),
    private val clock: Clock = Clock.System
) : LLMClient {

    @OptIn(InternalStructuredOutputApi::class)
    private companion object {
        private val logger = KotlinLogging.logger { }

        private const val DEFAULT_PATH = "v1beta/models"
        private const val DEFAULT_METHOD_GENERATE_CONTENT = "generateContent"
        private const val DEFAULT_METHOD_STREAM_GENERATE_CONTENT = "streamGenerateContent"

        init {
            // On class load register custom Google JSON schema generators for structured output.
            RegisteredBasicJsonSchemaGenerators[LLMProvider.Google] = GoogleBasicJsonSchemaGenerator
            RegisteredStandardJsonSchemaGenerators[LLMProvider.Google] = GoogleStandardJsonSchemaGenerator
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val httpClient = baseClient.config {
        defaultRequest {
            url(settings.baseUrl)
            url.parameters.append("key", apiKey)
            contentType(ContentType.Application.Json)
        }
        install(SSE)
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = settings.timeoutConfig.requestTimeoutMillis
            connectTimeoutMillis = settings.timeoutConfig.connectTimeoutMillis
            socketTimeoutMillis = settings.timeoutConfig.socketTimeoutMillis
        }
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.capabilities.contains(LLMCapability.Tools) || tools.isEmpty()) {
            "Model ${model.id} does not support tools"
        }

        val response = getGoogleResponse(prompt, model, tools)
        return processGoogleResponse(response).first()
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = flow {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        val request = createGoogleRequest(prompt, model, emptyList())

        try {
            httpClient.sse(
                urlString = "$DEFAULT_PATH/${model.id}:$DEFAULT_METHOD_STREAM_GENERATE_CONTENT",
                request = {
                    method = HttpMethod.Post
                    parameter("alt", "sse")
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
                        ?.data?.trim()?.let { json.decodeFromString<GoogleResponse>(it) }
                        ?.candidates?.firstOrNull()?.content
                        ?.parts?.forEach { part -> if (part is GooglePart.Text) emit(part.text) }
                }
            }
        } catch (e: SSEClientException) {
            e.response?.let { response ->
                logger.error { "Error from GoogleAI API: ${response.status}: ${e.message}" }
                error("Error from GoogleAI API: ${response.status}: ${e.message}")
            }
        } catch (e: Exception) {
            logger.error { "Exception during streaming: $e" }
            error(e.message ?: "Unknown error during streaming")
        }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> {
        logger.debug { "Executing prompt with multiple choices: $prompt with tools: $tools and model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.capabilities.contains(LLMCapability.Tools) || tools.isEmpty()) {
            "Model ${model.id} does not support tools"
        }
        require(model.capabilities.contains(LLMCapability.MultipleChoices)) {
            "Model ${model.id} does not support multiple choices"
        }

        return processGoogleResponse(getGoogleResponse(prompt, model, tools))
    }

    /**
     * Gets a response from the Google AI API.
     *
     * @param prompt The prompt to execute
     * @param model The model to use
     * @param tools The tools to include in the request
     * @return The raw response from the Google AI API
     */
    private suspend fun getGoogleResponse(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): GoogleResponse {
        logger.debug { "Getting Google response for prompt: $prompt with tools: $tools and model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.capabilities.contains(LLMCapability.Tools) || tools.isEmpty()) {
            "Model ${model.id} does not support tools"
        }

        val request = createGoogleRequest(prompt, model, tools)

        val response = withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post("$DEFAULT_PATH/${model.id}:$DEFAULT_METHOD_GENERATE_CONTENT") {
                setBody(request)
            }

            if (response.status.isSuccess()) {
                response.body<GoogleResponse>()
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from GoogleAI API: ${response.status}: $errorBody" }
                error("Error from GoogleAI API: ${response.status}: $errorBody")
            }
        }

        // https://discuss.ai.google.dev/t/gemini-2-5-pro-with-empty-response-text/81175/219
        if (response.candidates.isNotEmpty() && response.candidates.all { it.content?.parts?.isEmpty() == true }) {
            logger.warn { "Content `parts` field is missing in the response from GoogleAI API: $response" }
        }

        return response
    }

    /**
     * Creates a GoogleAI API request from a prompt.
     *
     * @param prompt The prompt to convert
     * @param model The model to use
     * @param tools Tools to include in the request
     * @return A formatted GoogleAI request
     */
    private fun createGoogleRequest(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): GoogleRequest {
        val systemMessageParts = mutableListOf<GooglePart.Text>()
        val contents = mutableListOf<GoogleContent>()
        val pendingCalls = mutableListOf<GooglePart.FunctionCall>()

        fun flushCalls() {
            if (pendingCalls.isNotEmpty()) {
                contents += GoogleContent(role = "model", parts = pendingCalls.toList())
                pendingCalls.clear()
            }
        }

        for (message in prompt.messages) {
            when (message) {
                is Message.System -> {
                    systemMessageParts.add(GooglePart.Text(message.content))
                }

                is Message.User -> {
                    flushCalls()
                    // User messages become 'user' role content
                    contents.add(message.toGoogleContent(model))
                }

                is Message.Assistant -> {
                    flushCalls()
                    contents.add(
                        GoogleContent(
                            role = "model",
                            parts = listOf(GooglePart.Text(message.content))
                        )
                    )
                }

                is Message.Tool.Result -> {
                    flushCalls()
                    contents.add(
                        GoogleContent(
                            role = "user",
                            parts = listOf(
                                GooglePart.FunctionResponse(
                                    functionResponse = GoogleData.FunctionResponse(
                                        id = message.id,
                                        name = message.tool,
                                        response = buildJsonObject { put("result", message.content) }
                                    )
                                )
                            )
                        )
                    )
                }

                is Message.Tool.Call -> {
                    pendingCalls += GooglePart.FunctionCall(
                        functionCall = GoogleData.FunctionCall(
                            id = message.id,
                            name = message.tool,
                            args = json.decodeFromString(message.content)
                        )
                    )
                }
            }
        }
        flushCalls()

        val googleTools = tools
            .map { tool ->
                val properties = (tool.requiredParameters + tool.optionalParameters)
                    .associate { it.name to buildGoogleParamType(it) }
                GoogleFunctionDeclaration(
                    name = tool.name,
                    description = tool.description,
                    parameters = buildJsonObject {
                        put("type", "object")
                        put("properties", JsonObject(properties))
                        putJsonArray("required") {
                            addAll(tool.requiredParameters.map { JsonPrimitive(it.name) })
                        }
                    }
                )
            }
            .takeIf { it.isNotEmpty() }
            ?.let { declarations -> listOf(GoogleTool(functionDeclarations = declarations)) }

        val googleSystemInstruction = systemMessageParts
            .takeIf { it.isNotEmpty() }
            ?.let { GoogleContent(parts = it) }

        val responseFormat: GoogleResponseFormat? = prompt.params.schema?.let { schema ->
            require(schema.capability in model.capabilities) {
                "Model ${model.id} does not support structured output schema ${schema.name}"
            }

            @Suppress("REDUNDANT_ELSE_IN_WHEN") // if more formats are added later
            when (schema) {
                is LLMParams.Schema.JSON.Basic -> GoogleResponseFormat(
                    responseMimeType = "application/json",
                    responseSchema = schema.schema,
                )

                is LLMParams.Schema.JSON.Standard -> GoogleResponseFormat(
                    responseMimeType = "application/json",
                    responseJsonSchema = schema.schema,
                )

                else -> throw IllegalArgumentException("Unsupported schema type: $schema")
            }
        }

        val generationConfig = GoogleGenerationConfig(
            responseMimeType = responseFormat?.responseMimeType,
            responseSchema = responseFormat?.responseSchema,
            responseJsonSchema = responseFormat?.responseJsonSchema,
            temperature = if (model.capabilities.contains(LLMCapability.Temperature)) prompt.params.temperature else null,
            candidateCount = if (model.capabilities.contains(LLMCapability.MultipleChoices)) prompt.params.numberOfChoices else null,
            maxOutputTokens = 2048,
            thinkingConfig = GoogleThinkingConfig(
                includeThoughts = prompt.params.includeThoughts.takeIf { it == true },
                thinkingBudget = prompt.params.thinkingBudget
            ).takeIf { it.includeThoughts != null || it.thinkingBudget != null }
        )

        val functionCallingConfig = when (val toolChoice = prompt.params.toolChoice) {
            LLMParams.ToolChoice.Auto -> GoogleFunctionCallingConfig(GoogleFunctionCallingMode.AUTO)
            LLMParams.ToolChoice.None -> GoogleFunctionCallingConfig(GoogleFunctionCallingMode.NONE)
            LLMParams.ToolChoice.Required -> GoogleFunctionCallingConfig(GoogleFunctionCallingMode.ANY)
            is LLMParams.ToolChoice.Named -> {
                GoogleFunctionCallingConfig(
                    GoogleFunctionCallingMode.ANY,
                    allowedFunctionNames = listOf(toolChoice.name)
                )
            }

            null -> null
        }

        return GoogleRequest(
            contents = contents,
            systemInstruction = googleSystemInstruction,
            tools = googleTools,
            generationConfig = generationConfig,
            toolConfig = GoogleToolConfig(functionCallingConfig),
        )
    }

    private fun Message.User.toGoogleContent(model: LLModel): GoogleContent {
        val contentParts = buildList {
            if (content.isNotEmpty() || attachments.isEmpty()) {
                add(GooglePart.Text(content))
            }
            attachments.forEach { attachment ->
                when (attachment) {
                    is Attachment.Image -> {
                        require(model.capabilities.contains(LLMCapability.Vision.Image)) {
                            "Model ${model.id} does not support images"
                        }

                        val blob: GoogleData.Blob = when (val content = attachment.content) {
                            is AttachmentContent.Binary -> GoogleData.Blob(attachment.mimeType, content.base64)
                            else -> throw IllegalArgumentException(
                                "Unsupported image attachment content: ${content::class}"
                            )
                        }

                        add(GooglePart.InlineData(blob))
                    }

                    is Attachment.Audio -> {
                        require(model.capabilities.contains(LLMCapability.Audio)) {
                            "Model ${model.id} does not support audio"
                        }

                        val blob: GoogleData.Blob = when (val content = attachment.content) {
                            is AttachmentContent.Binary -> GoogleData.Blob(attachment.mimeType, content.base64)
                            else -> throw IllegalArgumentException(
                                "Unsupported audio attachment content: ${content::class}"
                            )
                        }

                        add(GooglePart.InlineData(blob))
                    }

                    is Attachment.File -> {
                        require(model.capabilities.contains(LLMCapability.Document)) {
                            "Model ${model.id} does not support documents"
                        }

                        val blob: GoogleData.Blob = when (val content = attachment.content) {
                            is AttachmentContent.Binary -> GoogleData.Blob(attachment.mimeType, content.base64)
                            else -> throw IllegalArgumentException(
                                "Unsupported file attachment content: ${content::class}"
                            )
                        }

                        add(GooglePart.InlineData(blob))
                    }

                    is Attachment.Video -> {
                        require(model.capabilities.contains(LLMCapability.Vision.Video)) {
                            "Model ${model.id} does not support video"
                        }

                        val blob: GoogleData.Blob = when (val content = attachment.content) {
                            is AttachmentContent.Binary -> GoogleData.Blob(attachment.mimeType, content.base64)
                            else -> throw IllegalArgumentException(
                                "Unsupported video attachment content: ${content::class}"
                            )
                        }

                        add(GooglePart.InlineData(blob))
                    }
                }
            }
        }

        return GoogleContent(role = "user", parts = contentParts)
    }

    /**
     * Builds a parameter type definition for Google tools.
     *
     * @param param The tool parameter descriptor
     * @return A JSON element representing the parameter type
     */
    private fun buildGoogleParamType(param: ToolParameterDescriptor): JsonObject = buildJsonObject {
        put("description", JsonPrimitive(param.description))

        fun JsonObjectBuilder.putType(type: ToolParameterType) {
            when (type) {
                ToolParameterType.Boolean -> put("type", "boolean")
                ToolParameterType.Float -> put("type", "number")
                ToolParameterType.Integer -> put("type", "integer")
                ToolParameterType.String -> put("type", "string")

                is ToolParameterType.Enum -> {
                    put("type", "string")
                    putJsonArray("enum") { type.entries.forEach { add(it) } }
                }

                is ToolParameterType.List -> {
                    put("type", "array")
                    put("items", buildJsonObject { putType(type.itemsType) })
                }

                is ToolParameterType.Object -> {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            type.properties.forEach { property ->
                                put(
                                    property.name,
                                    buildJsonObject {
                                        putType(property.type)
                                        put("description", property.description)
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }

        putType(param.type)
    }

    /**
     * Processes a single Google AI API candidate into internal message format.
     *
     * @param candidate The candidate from the Google AI API response
     * @param metaInfo The metadata for the response
     * @return A list of response messages
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun processGoogleCandidate(candidate: GoogleCandidate, metaInfo: ResponseMetaInfo): List<Message.Response> {
        val parts = candidate.content?.parts.orEmpty()
        val responses = parts.map { part ->
            when (part) {
                is GooglePart.Text -> Message.Assistant(
                    content = part.text,
                    finishReason = candidate.finishReason,
                    metaInfo = metaInfo
                )

                is GooglePart.FunctionCall -> Message.Tool.Call(
                    id = Uuid.random().toString(),
                    tool = part.functionCall.name,
                    content = part.functionCall.args.toString(),
                    metaInfo = metaInfo
                )

                else -> error("Not supported part type: $part")
            }
        }

        return when {
            // Fix the situation when the model decides to both call tools and talk
            responses.any { it is Message.Tool.Call } -> responses.filterIsInstance<Message.Tool.Call>()
            // If no messages where returned, return an empty message and check finishReason
            responses.isEmpty() -> listOf(
                Message.Assistant(
                    content = "",
                    finishReason = candidate.finishReason,
                    metaInfo = metaInfo
                )
            )
            // Just return responses
            else -> responses
        }
    }

    /**
     * Processes the Google AI API response into a list of choices.
     *
     * @param response The raw response from the Google AI API
     * @return A list of choices, where each choice is a list of response messages
     */
    private fun processGoogleResponse(response: GoogleResponse): List<List<Message.Response>> {
        if (response.candidates.isEmpty()) {
            logger.error { "Empty candidates in Gemini response" }
            error("Empty candidates in Gemini response")
        }

        // Extract token count from the response
        val inputTokensCount = response.usageMetadata?.promptTokenCount
        val outputTokensCount = response.usageMetadata?.candidatesTokenCount
        val totalTokensCount = response.usageMetadata?.totalTokenCount

        val metaInfo = ResponseMetaInfo.create(
            clock,
            totalTokensCount = totalTokensCount,
            inputTokensCount = inputTokensCount,
            outputTokensCount = outputTokensCount
        )

        return response.candidates.map { candidate ->
            processGoogleCandidate(candidate, metaInfo)
        }
    }

    /**
     * Moderates the given prompt using the specified language model.
     * This method is not supported by the Google API and will throw an exception when invoked.
     *
     * @param prompt The prompt to be evaluated for moderation.
     * @param model The language model to use for moderation.
     * @return This method does not return a result as moderation is not supported by the Google API.
     * @throws UnsupportedOperationException Always thrown since moderation is not supported.
     */
    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by Google API" }
        throw UnsupportedOperationException("Moderation is not supported by Google API.")
    }
}
