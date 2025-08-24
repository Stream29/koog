package ai.koog.prompt.executor.clients.openai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.utils.SuitableForIO
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.ModerationCategoryResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.openai.models.Content
import ai.koog.prompt.executor.clients.openai.models.InputContent
import ai.koog.prompt.executor.clients.openai.models.Item
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionRequest
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionStreamResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIContentPart
import ai.koog.prompt.executor.clients.openai.models.OpenAIEmbeddingRequest
import ai.koog.prompt.executor.clients.openai.models.OpenAIEmbeddingResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.models.OpenAIModalities
import ai.koog.prompt.executor.clients.openai.models.OpenAIOutputFormat
import ai.koog.prompt.executor.clients.openai.models.OpenAIResponsesAPIRequest
import ai.koog.prompt.executor.clients.openai.models.OpenAIResponsesAPIResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIResponsesTool
import ai.koog.prompt.executor.clients.openai.models.OpenAIResponsesToolChoice
import ai.koog.prompt.executor.clients.openai.models.OpenAIStaticContent
import ai.koog.prompt.executor.clients.openai.models.OpenAIStreamEvent
import ai.koog.prompt.executor.clients.openai.models.OpenAITextConfig
import ai.koog.prompt.executor.clients.openai.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.structure.OpenAIBasicJsonSchemaGenerator
import ai.koog.prompt.executor.clients.openai.structure.OpenAIStandardJsonSchemaGenerator
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
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents the settings for configuring an OpenAI client.
 *
 * @property baseUrl The base URL of the OpenAI API. Defaults to "https://api.openai.com".
 * @property timeoutConfig Configuration for connection timeouts, including request, connect, and socket timeouts.
 * @property chatCompletionsPath The path of the OpenAI Chat Completions API. Defaults to "v1/chat/completions".
 * @property embeddingsPath The path of the OpenAI Embeddings API. Defaults to "v1/embeddings".
 * @property moderationsPath The path of the OpenAI Moderations API. Defaults to "v1/moderations".
 */
public class OpenAIClientSettings(
    baseUrl: String = "https://api.openai.com",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    chatCompletionsPath: String = "v1/chat/completions",
    public val responsesAPIPath: String = "v1/responses",
    public val embeddingsPath: String = "v1/embeddings",
    public val moderationsPath: String = "v1/moderations",
) : OpenAIBasedSettings(baseUrl, chatCompletionsPath, timeoutConfig)

/**
 * Implementation of [LLMClient] for OpenAI API.
 * Uses Ktor HttpClient to communicate with the OpenAI API.
 *
 * @param apiKey The API key for the OpenAI API
 * @param settings The base URL and timeouts for the OpenAI API, defaults to "https://api.openai.com" and 900 s
 * @param clock Clock instance used for tracking response metadata timestamps.
 */
@OptIn(ExperimentalAtomicApi::class)
public open class OpenAILLMClient(
    apiKey: String,
    private val settings: OpenAIClientSettings = OpenAIClientSettings(),
    baseClient: HttpClient = HttpClient(),
    clock: Clock = Clock.System,
) : AbstractOpenAILLMClient<OpenAIChatCompletionResponse, OpenAIChatCompletionStreamResponse>(
    apiKey,
    settings,
    baseClient,
    clock
),
    LLMEmbeddingProvider {

    @OptIn(InternalStructuredOutputApi::class)
    private companion object {
        private val staticLogger = KotlinLogging.logger { }

        init {
            // On class load register custom OpenAI JSON schema generators for structured output.
            RegisteredBasicJsonSchemaGenerators[LLMProvider.OpenAI] = OpenAIBasicJsonSchemaGenerator
            RegisteredStandardJsonSchemaGenerators[LLMProvider.OpenAI] = OpenAIStandardJsonSchemaGenerator
        }
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
        val chatParams = params.toOpenAIChatParams()
        val modalities = if (chatParams.audio != null && model.supports(LLMCapability.Audio))
            listOf(OpenAIModalities.Text, OpenAIModalities.Audio) else null

        val responseFormat = createResponseFormat(chatParams.schema, model)

        val request = OpenAIChatCompletionRequest(
            messages = messages,
            model = model.id,
            audio = chatParams.audio,
            frequencyPenalty = chatParams.frequencyPenalty,
            logprobs = chatParams.logprobs,
            maxCompletionTokens = chatParams.maxTokens,
            modalities = modalities,
            numberOfChoices = model.takeIf { it.supports(LLMCapability.MultipleChoices) }
                ?.let { chatParams.numberOfChoices },
            parallelToolCalls = chatParams.parallelToolCalls,
            prediction = chatParams.speculation?.let { OpenAIStaticContent(Content.Text(it)) },
            presencePenalty = chatParams.presencePenalty,
            promptCacheKey = chatParams.promptCacheKey,
            reasoningEffort = chatParams.reasoningEffort,
            responseFormat = responseFormat,
            safetyIdentifier = chatParams.safetyIdentifier,
            serviceTier = chatParams.serviceTier,
            stop = chatParams.stop,
            store = chatParams.store,
            stream = stream,
            temperature = chatParams.temperature,
            toolChoice = toolChoice,
            tools = tools,
            topLogprobs = chatParams.topLogprobs,
            topP = chatParams.topP,
            user = chatParams.user,
            webSearchOptions = chatParams.webSearchOptions,
        )

        return json.encodeToString(request)
    }

    private fun serializeResponsesAPIRequest(
        messages: List<Item>,
        model: LLModel,
        tools: List<OpenAIResponsesTool>?,
        toolChoice: OpenAIResponsesToolChoice?,
        params: OpenAIResponsesParams,
        stream: Boolean
    ): String {
        val responseFormat = params.schema?.let { schema ->
            require(schema.capability in model.capabilities) {
                "Model ${model.id} does not support structured output schema ${schema.name}"
            }
            when (schema) {
                is LLMParams.Schema.JSON -> OpenAITextConfig(
                    format = OpenAIOutputFormat.JsonSchema(
                        name = schema.name,
                        schema = schema.schema,
                        strict = true
                    )
                )
            }
        }

        val request = OpenAIResponsesAPIRequest(
            background = params.background,
            include = params.include,
            input = messages,
            maxOutputTokens = params.maxTokens,
            maxToolCalls = params.maxToolCalls,
            model = model.id,
            parallelToolCalls = params.parallelToolCalls,
            promptCacheKey = params.promptCacheKey,
            reasoning = params.reasoning,
            safetyIdentifier = params.safetyIdentifier,
            serviceTier = params.serviceTier,
            store = params.store,
            stream = stream,
            temperature = params.temperature,
            text = responseFormat,
            toolChoice = toolChoice,
            tools = tools,
            topLogprobs = params.topLogprobs,
            topP = params.topP,
            truncation = params.truncation,
            user = params.user,
        )

        return json.encodeToString(request)
    }

    override fun processProviderChatResponse(response: OpenAIChatCompletionResponse): List<LLMChoice> {
        require(response.choices.isNotEmpty()) { "Empty choices in response" }
        return response.choices.map { it.toMessageResponses(createMetaInfo(response.usage)) }
    }

    override fun decodeStreamingResponse(data: String): OpenAIChatCompletionStreamResponse =
        json.decodeFromString(data)

    override fun decodeResponse(data: String): OpenAIChatCompletionResponse =
        json.decodeFromString(data)

    override fun processStreamingChunk(chunk: OpenAIChatCompletionStreamResponse): String? =
        chunk.choices.firstOrNull()?.delta?.content

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        return selectExecutionStrategy(prompt, model) { params ->
            when (params) {
                is OpenAIResponsesParams -> {
                    val response = getResponseWithResponsesAPI(prompt, params, model, tools)
                    processResponsesAPIResponse(response)
                }

                is OpenAIChatParams -> super.execute(prompt, model, tools)
            }
        }
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        return selectExecutionStrategy(prompt, model) { params ->
            when (params) {
                is OpenAIResponsesParams -> executeResponsesStreaming(prompt, model, params)
                is OpenAIChatParams -> super.executeStreaming(prompt, model)
            }
        }
    }

    private fun executeResponsesStreaming(prompt: Prompt, model: LLModel, params: OpenAIResponsesParams): Flow<String> =
        flow {
            logger.debug { "Executing streaming prompt: $prompt with model: $model" }

            val messages = convertPromptToInput(prompt, model)
            val request = serializeResponsesAPIRequest(
                messages = messages,
                model = model,
                tools = emptyList(),
                toolChoice = prompt.params.toolChoice?.toOpenAIResponseToolChoice(),
                params = params,
                stream = true
            )

            try {
                httpClient.sse(
                    urlString = settings.responsesAPIPath,
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
                            .data
                            ?.let { json.decodeFromString<OpenAIStreamEvent>(it) }
                            ?.takeIf { it is OpenAIStreamEvent.ResponseOutputTextDelta } // TODO("handle other events")
                            ?.let { (it as OpenAIStreamEvent.ResponseOutputTextDelta).delta }
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
    ): List<LLMChoice> = super.executeMultipleChoices(prompt, model, tools)


    /**
     * Embeds the given text using the OpenAI embeddings API.
     *
     * @param text The text to embed.
     * @param model The model to use for embedding. Must have the Embed capability.
     * @return A list of floating-point values representing the embedding.
     * @throws IllegalArgumentException if the model does not have the Embed capability.
     */
    override suspend fun embed(text: String, model: LLModel): List<Double> {
        model.requireCapability(LLMCapability.Embed)

        logger.debug { "Embedding text with model: ${model.id}" }

        val request = OpenAIEmbeddingRequest(
            model = model.id,
            input = text
        )

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post(settings.embeddingsPath) {
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val openAIResponse = response.body<OpenAIEmbeddingResponse>()
                if (openAIResponse.data.isNotEmpty()) {
                    openAIResponse.data.first().embedding
                } else {
                    logger.error { "Empty data in OpenAI embedding response" }
                    error("Empty data in OpenAI embedding response")
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from OpenAI API: ${response.status}: $errorBody" }
                error("Error from OpenAI API: ${response.status}: $errorBody")
            }
        }
    }

    /**
     * Moderates text and image content based on the provided model's capabilities.
     *
     * @param prompt The prompt containing text messages and optional attachments to be moderated.
     * @param model The language model to use for moderation. Must have the `Moderation` capability.
     * @return The moderation result, including flagged content, categories, scores, and associated metadata.
     * @throws IllegalArgumentException If the specified model does not support moderation.
     */
    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.debug { "Moderating text and image content with model: $model" }

        model.requireCapability(LLMCapability.Moderation)

        require(prompt.messages.isNotEmpty()) { "Can't moderate an empty prompt" }

        val input = prompt.messages
            .map { message ->
                if (message is Message.WithAttachments) {
                    require(message.attachments.all { it is Attachment.Image }) {
                        "Only image attachments are supported for moderation"
                    }
                }

                message.toMessageContent(model)
            }
            .let { contents ->
                /*
                 If all messages contain only text, merge it all in a single text input,
                 to support OpenAI-compatible providers that do not support attachments.

                 Otherwise create a single content instance with all the parts
                 */
                if (contents.all { it is Content.Text }) {
                    val text = contents.joinToString(separator = "\n\n") { (it as Content.Text).value }

                    Content.Text(text)
                } else {
                    val parts = contents.flatMap { content ->
                        when (content) {
                            is Content.Parts -> content.value
                            is Content.Text -> listOf(OpenAIContentPart.Text(content.value))
                        }
                    }

                    Content.Parts(parts)
                }
            }

        val request = OpenAIModerationRequest(
            input = input,
            model = model.id
        )

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post(settings.moderationsPath) {
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val openAIResponse = response.body<OpenAIModerationResponse>()
                if (openAIResponse.results.isNotEmpty()) {
                    val result = openAIResponse.results.first()

                    // Convert OpenAI categories to a map
                    val categories = mapOf(
                        ModerationCategory.Harassment to result.categories.harassment,
                        ModerationCategory.HarassmentThreatening to result.categories.harassmentThreatening,
                        ModerationCategory.Hate to result.categories.hate,
                        ModerationCategory.HateThreatening to result.categories.hateThreatening,
                        ModerationCategory.Sexual to result.categories.sexual,
                        ModerationCategory.SexualMinors to result.categories.sexualMinors,
                        ModerationCategory.Violence to result.categories.violence,
                        ModerationCategory.ViolenceGraphic to result.categories.violenceGraphic,
                        ModerationCategory.SelfHarm to result.categories.selfHarm,
                        ModerationCategory.SelfHarmIntent to result.categories.selfHarmIntent,
                        ModerationCategory.SelfHarmInstructions to result.categories.selfHarmInstructions,
                        ModerationCategory.Illicit to (result.categories.illicit ?: false),
                        ModerationCategory.IllicitViolent to (result.categories.illicitViolent ?: false)
                    )

                    // Convert OpenAI category scores to a map
                    val categoryScores = mapOf(
                        ModerationCategory.Harassment to result.categoryScores.harassment,
                        ModerationCategory.HarassmentThreatening to result.categoryScores.harassmentThreatening,
                        ModerationCategory.Hate to result.categoryScores.hate,
                        ModerationCategory.HateThreatening to result.categoryScores.hateThreatening,
                        ModerationCategory.Sexual to result.categoryScores.sexual,
                        ModerationCategory.SexualMinors to result.categoryScores.sexualMinors,
                        ModerationCategory.Violence to result.categoryScores.violence,
                        ModerationCategory.ViolenceGraphic to result.categoryScores.violenceGraphic,
                        ModerationCategory.SelfHarm to result.categoryScores.selfHarm,
                        ModerationCategory.SelfHarmIntent to result.categoryScores.selfHarmIntent,
                        ModerationCategory.SelfHarmInstructions to result.categoryScores.selfHarmInstructions,
                        ModerationCategory.Illicit to (result.categoryScores.illicit ?: 0.0),
                        ModerationCategory.IllicitViolent to (result.categoryScores.illicitViolent ?: 0.0)
                    )

                    // Convert category applied input types if available
                    val categoryAppliedInputTypes = result.categoryAppliedInputTypes?.let { appliedTypes ->
                        buildMap {
                            appliedTypes.harassment?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.Harassment, it) }
                            appliedTypes.harassmentThreatening?.map {
                                ModerationResult.InputType.valueOf(it.uppercase())
                            }
                                ?.let { put(ModerationCategory.HarassmentThreatening, it) }
                            appliedTypes.hate?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.Hate, it) }
                            appliedTypes.hateThreatening?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.HateThreatening, it) }
                            appliedTypes.sexual?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.Sexual, it) }
                            appliedTypes.sexualMinors?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.SexualMinors, it) }
                            appliedTypes.violence?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.Violence, it) }
                            appliedTypes.violenceGraphic?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.ViolenceGraphic, it) }
                            appliedTypes.selfHarm?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.SelfHarm, it) }
                            appliedTypes.selfHarmIntent?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.SelfHarmIntent, it) }
                            appliedTypes.selfHarmInstructions?.map {
                                ModerationResult.InputType.valueOf(it.uppercase())
                            }
                                ?.let { put(ModerationCategory.SelfHarmInstructions, it) }
                            appliedTypes.illicit?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.Illicit, it) }
                            appliedTypes.illicitViolent?.map { ModerationResult.InputType.valueOf(it.uppercase()) }
                                ?.let { put(ModerationCategory.IllicitViolent, it) }
                        }
                    } ?: emptyMap()

                    ModerationResult(
                        isHarmful = result.flagged,
                        categories = categories.mapValues { (category, detected) ->
                            ModerationCategoryResult(
                                detected,
                                categoryScores[category],
                                categoryAppliedInputTypes[category] ?: emptyList()
                            )
                        }
                    )
                } else {
                    logger.error { "Empty results in OpenAI moderation response" }
                    error("Empty results in OpenAI moderation response")
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from OpenAI API: ${response.status}: $errorBody" }
                error("Error from OpenAI API: ${response.status}: $errorBody")
            }
        }
    }


    private suspend fun getResponseWithResponsesAPI(
        prompt: Prompt,
        params: OpenAIResponsesParams,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): OpenAIResponsesAPIResponse {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        if (tools.isNotEmpty()) {
            model.requireCapability(LLMCapability.Tools)
        }

        val llmTools = tools.takeIf { it.isNotEmpty() }?.map { it.toResponsesTool() }
        val messages = convertPromptToInput(prompt, model)

        val request = serializeResponsesAPIRequest(
            messages,
            model,
            llmTools,
            prompt.params.toolChoice?.toOpenAIResponseToolChoice(),
            params,
            false
        )

        return withContext(Dispatchers.SuitableForIO) {
            val response = httpClient.post(settings.responsesAPIPath) {
                setBody(request)
            }

            if (response.status.isSuccess()) {
                response.body<OpenAIResponsesAPIResponse>()
            } else {
                val errorBody = response.bodyAsText()
                logger.error { "Error from $clientName API: ${response.status}: $errorBody" }
                error("Error from $clientName API: ${response.status}: $errorBody")
            }
        }
    }

    private fun ToolDescriptor.toResponsesTool(): OpenAIResponsesTool.Function =
        OpenAIResponsesTool.Function(
            name = name,
            parameters = paramsToJsonObject(),
            description = description
        )

    @OptIn(ExperimentalUuidApi::class)
    private fun convertPromptToInput(prompt: Prompt, model: LLModel): List<Item> {
        val messages = mutableListOf<Item>()
        val pendingCalls = mutableListOf<Item.FunctionToolCall>()

        fun flushPendingCalls() {
            if (pendingCalls.isNotEmpty()) {
                messages += pendingCalls
                pendingCalls.clear()
            }
        }

        with(messages) {
            prompt.messages.forEach { message ->
                when (message) {
                    is Message.System -> {
                        flushPendingCalls()
                        add(Item.InputMessage(role = "developer", content = listOf(InputContent.Text(message.content))))
                    }

                    is Message.User -> {
                        flushPendingCalls()
                        add(Item.InputMessage(role = "user", content = message.toInputMessage(model)))
                    }

                    is Message.Assistant -> {
                        flushPendingCalls()
                        add(Item.InputMessage(role = "assistant", content = listOf(InputContent.Text(message.content))))
                    }

                    is Message.Tool.Result -> {
                        flushPendingCalls()
                        add(
                            Item.FunctionToolCallOutput(
                                callId = message.id ?: Uuid.random().toString(),
                                output = message.content
                            )
                        )
                    }

                    is Message.Tool.Call -> {
                        pendingCalls += Item.FunctionToolCall(
                            callId = message.id ?: Uuid.random().toString(),
                            name = message.tool,
                            arguments = message.content
                        )
                    }
                }
            }
        }
        flushPendingCalls()

        return messages
    }

    private fun Message.toInputMessage(model: LLModel): List<InputContent> {
        if (this !is Message.WithAttachments || attachments.isEmpty()) {
            return listOf(InputContent.Text(content))
        }

        val parts = buildList {
            if (content.isNotEmpty()) {
                add(InputContent.Text(content))
            }

            attachments.forEach { attachment ->
                when (attachment) {
                    is Attachment.Image -> {
                        model.requireCapability(LLMCapability.Vision.Image)

                        val imageUrl: String = when (val content = attachment.content) {
                            is AttachmentContent.URL -> content.url
                            is AttachmentContent.Binary -> "data:${attachment.mimeType};base64,${content.base64}"
                            else -> throw IllegalArgumentException("Unsupported image attachment content: ${content::class}")
                        }

                        add(InputContent.Image(imageUrl = imageUrl))
                    }

                    is Attachment.File -> {
                        model.requireCapability(LLMCapability.Document)

                        val fileData = when (val content = attachment.content) {
                            is AttachmentContent.Binary -> "data:${attachment.mimeType};base64,${content.base64}"
                            else -> null
                        }

                        val fileUrl = when (val content = attachment.content) {
                            is AttachmentContent.URL -> content.url
                            else -> null
                        }

                        add(InputContent.File(fileData = fileData, fileUrl = fileUrl, filename = attachment.fileName))
                    }

                    else -> throw IllegalArgumentException("Unsupported attachment type: $attachment, for model: $model with Responses API")
                }
            }
        }

        return parts
    }

    private fun processResponsesAPIResponse(response: OpenAIResponsesAPIResponse): List<Message.Response> {
        require(response.output.isNotEmpty()) { "Empty output in response" }

        val metaInfo = ResponseMetaInfo.create(
            clock,
            totalTokensCount = response.usage?.totalTokens,
            inputTokensCount = response.usage?.inputTokens,
            outputTokensCount = response.usage?.outputTokens
        )

        return response.output.map { output ->
            when (output) {
                is Item.FunctionToolCall -> Message.Tool.Call(
                    id = output.callId,
                    tool = output.name,
                    content = output.arguments,
                    metaInfo = metaInfo
                )

                is Item.OutputMessage -> Message.Assistant(
                    content = output.text(),
                    finishReason = output.status.name,
                    metaInfo = metaInfo
                )

                else -> error("Unexpected response from $clientName: no tool calls and no content")
            }
        }
    }

    private fun LLMParams.ToolChoice.toOpenAIResponseToolChoice() = when (this) {
        LLMParams.ToolChoice.Auto -> OpenAIResponsesToolChoice.Mode("auto")
        LLMParams.ToolChoice.None -> OpenAIResponsesToolChoice.Mode("none")
        LLMParams.ToolChoice.Required -> OpenAIResponsesToolChoice.Mode("required")
        is LLMParams.ToolChoice.Named -> OpenAIResponsesToolChoice.FunctionTool(name = name)
    }

    private fun determineParams(params: LLMParams, model: LLModel): OpenAIParams = when {
        params is OpenAIResponsesParams && model.supports(LLMCapability.OpenAIEndpoint.Responses) -> params
        params is OpenAIChatParams && model.supports(LLMCapability.OpenAIEndpoint.Completions) -> params
        model.supports(LLMCapability.OpenAIEndpoint.Responses) -> params.toOpenAIResponsesParams()
        model.supports(LLMCapability.OpenAIEndpoint.Completions) -> params.toOpenAIChatParams()
        else -> error("Unsupported OpenAI API endpoint for model: ${model.id}")
    }

    private inline fun <T> selectExecutionStrategy(
        prompt: Prompt,
        model: LLModel,
        action: (OpenAIParams) -> T
    ): T = action(determineParams(prompt.params, model))
}
