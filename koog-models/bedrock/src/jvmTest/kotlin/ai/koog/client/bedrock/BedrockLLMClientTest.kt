package ai.koog.client.bedrock

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.client.ConnectionTimeoutConfig
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.ApplyGuardrailRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ApplyGuardrailResponse
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseResponse
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseStreamResponse
import aws.sdk.kotlin.services.bedrockruntime.model.GetAsyncInvokeRequest
import aws.sdk.kotlin.services.bedrockruntime.model.GetAsyncInvokeResponse
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailAction
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailAssessment
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentFilter
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentFilterConfidence
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentFilterType
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentPolicyAction
import aws.sdk.kotlin.services.bedrockruntime.model.GuardrailContentPolicyAssessment
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelResponse
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithBidirectionalStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithBidirectionalStreamResponse
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithResponseStreamResponse
import aws.sdk.kotlin.services.bedrockruntime.model.ListAsyncInvokesRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ListAsyncInvokesResponse
import aws.sdk.kotlin.services.bedrockruntime.model.StartAsyncInvokeRequest
import aws.sdk.kotlin.services.bedrockruntime.model.StartAsyncInvokeResponse
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BedrockLLMClientTest {
    @Test
    fun `can create BedrockLLMClient`() {
        val client = BedrockLLMClient(
            awsAccessKeyId = "test-key",
            awsSecretAccessKey = "test-secret",
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = Clock.System
        )

        assertNotNull(client)
    }

    @Test
    fun `can create BedrockModel with custom inference prefix`() {
        val originalModel = BedrockModels.AnthropicClaude4Sonnet

        val euModel = originalModel.withInferenceProfile(BedrockInferencePrefixes.EU.prefix)
        val apModel = originalModel.withInferenceProfile(BedrockInferencePrefixes.AP.prefix)

        assertTrue(originalModel.id.startsWith(BedrockInferencePrefixes.US.prefix))
        assertTrue(originalModel.id.contains("us.anthropic"))

        assertTrue(euModel.id.startsWith(BedrockInferencePrefixes.EU.prefix))
        assertFalse(euModel.id.contains("us.anthropic"))
        assertTrue(apModel.id.startsWith(BedrockInferencePrefixes.AP.prefix))
        assertFalse(apModel.id.contains("us.anthropic"))

        assertEquals(originalModel.provider, euModel.provider)
        assertEquals(originalModel.capabilities, euModel.capabilities)
        assertEquals(originalModel.contextLength, euModel.contextLength)
        assertEquals(originalModel.maxOutputTokens, euModel.maxOutputTokens)
    }

    @Test
    fun `withInferencePrefix throws exception for non-Bedrock models`() {
        val nonBedrockModel = LLModel(
            provider = LLMProvider.Anthropic,
            id = "claude-3-sonnet-20240229",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = 200_000
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            nonBedrockModel.withInferenceProfile("eu")
        }

        assertNotNull(exception.message, "Exception message should not be null")
        assertTrue(exception.message!!.contains("withInferencePrefix() can only be used with Bedrock models"))
        assertTrue(exception.message!!.contains("model provider is Anthropic"))
    }

    @Test
    fun `verify all BedrockModels are properly configured`() {
        // Test Claude 3 models with full capabilities
        val claude3Models = listOf(
            BedrockModels.AnthropicClaude3Opus,
            BedrockModels.AnthropicClaude3Sonnet,
            BedrockModels.AnthropicClaude3Haiku
        )

        claude3Models.forEach { model ->
            assertTrue(model.provider is LLMProvider.Bedrock)
            assertTrue(model.capabilities.contains(LLMCapability.Completion))
            assertTrue(model.capabilities.contains(LLMCapability.Temperature))
            assertTrue(model.capabilities.contains(LLMCapability.Tools))
            assertTrue(model.capabilities.contains(LLMCapability.ToolChoice))
            assertTrue(model.capabilities.contains(LLMCapability.Vision.Image))
            assertTrue(model.capabilities.contains(LLMCapability.Schema.JSON.Full))
        }

        // Test Claude 3.5 models with full capabilities
        val claude35Models = listOf(
            BedrockModels.AnthropicClaude35SonnetV2,
            BedrockModels.AnthropicClaude35Haiku
        )

        claude35Models.forEach { model ->
            assertTrue(model.provider is LLMProvider.Bedrock)
            assertTrue(model.capabilities.contains(LLMCapability.Completion))
            assertTrue(model.capabilities.contains(LLMCapability.Temperature))
            assertTrue(model.capabilities.contains(LLMCapability.Tools))
            assertTrue(model.capabilities.contains(LLMCapability.ToolChoice))
            assertTrue(model.capabilities.contains(LLMCapability.Vision.Image))
            assertTrue(model.capabilities.contains(LLMCapability.Schema.JSON.Full))
        }

        // Test Claude 4 models with full capabilities
        val claude4Models = listOf(
            BedrockModels.AnthropicClaude4Opus,
            BedrockModels.AnthropicClaude4Sonnet
        )

        claude4Models.forEach { model ->
            assertTrue(model.provider is LLMProvider.Bedrock)
            assertTrue(model.capabilities.contains(LLMCapability.Completion))
            assertTrue(model.capabilities.contains(LLMCapability.Temperature))
            assertTrue(model.capabilities.contains(LLMCapability.Tools))
            assertTrue(model.capabilities.contains(LLMCapability.ToolChoice))
            assertTrue(model.capabilities.contains(LLMCapability.Vision.Image))
            assertTrue(model.capabilities.contains(LLMCapability.Schema.JSON.Full))
        }

        // Test older Claude models with standard capabilities
        val olderClaudeModels = listOf(
            BedrockModels.AnthropicClaude21,
            BedrockModels.AnthropicClaudeInstant
        )

        olderClaudeModels.forEach { model ->
            assertTrue(model.provider is LLMProvider.Bedrock)
            assertTrue(model.id.contains("anthropic.claude"))
            assertTrue(model.capabilities.contains(LLMCapability.Completion))
            assertTrue(model.capabilities.contains(LLMCapability.Temperature))
        }

        // Test Amazon Nova models
        val novaModels = listOf(
            BedrockModels.AmazonNovaMicro,
            BedrockModels.AmazonNovaLite,
            BedrockModels.AmazonNovaPro,
            BedrockModels.AmazonNovaPremier
        )

        novaModels.forEach { model ->
            assertTrue(model.provider is LLMProvider.Bedrock)
            assertTrue(model.id.contains("amazon.nova"))
            assertTrue(model.capabilities.contains(LLMCapability.Completion))
            assertTrue(model.capabilities.contains(LLMCapability.Temperature))
        }

        // Test AI21 models
        val ai21Models = listOf(
            BedrockModels.AI21JambaLarge,
            BedrockModels.AI21JambaMini
        )

        ai21Models.forEach { model ->
            assertTrue(model.provider is LLMProvider.Bedrock)
            assertTrue(model.id.contains("ai21.jamba"))
            assertTrue(model.capabilities.contains(LLMCapability.Completion))
            assertTrue(model.capabilities.contains(LLMCapability.Temperature))
        }

        // Test Meta models
        val metaModels = listOf(
            BedrockModels.MetaLlama3_0_8BInstruct,
            BedrockModels.MetaLlama3_0_70BInstruct
        )

        metaModels.forEach { model ->
            assertTrue(model.provider is LLMProvider.Bedrock)
            assertTrue(model.id.contains("meta.llama"))
            assertTrue(model.capabilities.contains(LLMCapability.Completion))
            assertTrue(model.capabilities.contains(LLMCapability.Temperature))
        }
    }

    @Test
    fun `client configuration options work correctly`() {
        val customSettings = BedrockClientSettings(
            region = BedrockRegions.EU_WEST_1.regionCode,
            endpointUrl = "https://custom.endpoint.com",
            maxRetries = 5,
            enableLogging = true,
            timeoutConfig = ConnectionTimeoutConfig(
                requestTimeoutMillis = 120_000,
                connectTimeoutMillis = 10_000,
                socketTimeoutMillis = 120_000
            )
        )

        val client = BedrockLLMClient(
            awsAccessKeyId = "test-key",
            awsSecretAccessKey = "test-secret",
            settings = customSettings,
            clock = Clock.System
        )

        assertNotNull(client)
        assertEquals(BedrockRegions.EU_WEST_1.regionCode, customSettings.region)
        assertEquals("https://custom.endpoint.com", customSettings.endpointUrl)
        assertEquals(5, customSettings.maxRetries)
        assertEquals(true, customSettings.enableLogging)
    }

    @Test
    fun `model IDs follow expected patterns`() {
        // Verify Anthropic model IDs
        assertTrue(BedrockModels.AnthropicClaude4Opus.id.contains("anthropic.claude-opus-4-20250514-v1:0"))
        assertTrue(BedrockModels.AnthropicClaude4Sonnet.id.contains("anthropic.claude-sonnet-4-20250514-v1:0"))
        assertTrue(BedrockModels.AnthropicClaude35SonnetV2.id.contains("anthropic.claude-3-5-sonnet-20241022-v2:0"))
        assertTrue(BedrockModels.AnthropicClaude35Haiku.id.contains("anthropic.claude-3-5-haiku-20241022-v1:0"))
        assertTrue(BedrockModels.AnthropicClaude3Opus.id.contains("anthropic.claude-3-opus"))
        assertTrue(BedrockModels.AnthropicClaude3Sonnet.id.contains("anthropic.claude-3-sonnet"))
        assertTrue(BedrockModels.AnthropicClaude3Haiku.id.contains("anthropic.claude-3-haiku"))
        assertTrue(BedrockModels.AnthropicClaude21.id.contains("anthropic.claude-v2:1"))
        assertTrue(BedrockModels.AnthropicClaudeInstant.id.contains("anthropic.claude-instant-v1"))

        // Verify Amazon Nova model IDs
        assertTrue(BedrockModels.AmazonNovaMicro.id.contains("amazon.nova"))
        assertTrue(BedrockModels.AmazonNovaLite.id.contains("amazon.nova"))
        assertTrue(BedrockModels.AmazonNovaPro.id.contains("amazon.nova"))
        assertTrue(BedrockModels.AmazonNovaPremier.id.contains("amazon.nova"))

        // Verify AI21 model IDs
        assertTrue(BedrockModels.AI21JambaLarge.id.contains("ai21.jamba-1-5-large-v1:0"))
        assertTrue(BedrockModels.AI21JambaMini.id.contains("ai21.jamba-1-5-mini-v1:0"))

        // Verify Meta Llama model IDs
        assertTrue(BedrockModels.MetaLlama3_0_8BInstruct.id.contains("meta.llama3-8b-instruct-v1:0"))
        assertTrue(BedrockModels.MetaLlama3_0_70BInstruct.id.contains("meta.llama3-70b-instruct-v1:0"))
    }

    @Test
    fun testToolCallGeneration() = runTest {
        val tools = listOf(
            ToolDescriptor(
                name = "get_weather",
                description = "Get current weather for a city",
                requiredParameters = listOf(
                    ToolParameterDescriptor("city", "The city name", ToolParameterType.String)
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor("units", "Temperature units", ToolParameterType.String)
                )
            )
        )

        val prompt = Prompt.build("test") {
            user("What's the weather in Paris?")
        }

        // Test that Claude 3 models support tools
        val claudeModel = BedrockModels.AnthropicClaude3Sonnet
        assertTrue(claudeModel.capabilities.contains(LLMCapability.Tools))

        // Test that Claude 3.5 models support tools (with advanced capabilities)
        val claude35Sonnet = BedrockModels.AnthropicClaude35SonnetV2
        val claude35Haiku = BedrockModels.AnthropicClaude35Haiku
        assertTrue(claude35Sonnet.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude35Sonnet.capabilities.contains(LLMCapability.ToolChoice))
        assertTrue(claude35Haiku.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude35Haiku.capabilities.contains(LLMCapability.ToolChoice))

        // Test that Claude 4 models support tools (with advanced capabilities)
        val claude4Opus = BedrockModels.AnthropicClaude4Opus
        val claude4Sonnet = BedrockModels.AnthropicClaude4Sonnet
        assertTrue(claude4Opus.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude4Opus.capabilities.contains(LLMCapability.ToolChoice))
        assertTrue(claude4Sonnet.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude4Sonnet.capabilities.contains(LLMCapability.ToolChoice))

        // Mock client for testing tool call request generation
        val client = BedrockLLMClient(
            awsAccessKeyId = "test-key",
            awsSecretAccessKey = "test-secret",
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = Clock.System
        )

        // Verify that older Claude models don't support tools
        val olderClaudeModel = BedrockModels.AnthropicClaude21
        assertFails {
            client.execute(prompt, olderClaudeModel, tools)
        }
    }

    @Test
    fun testAnthropicToolCallResponseParsing() {
        // Simulate Anthropic Claude response with tool calls
        val mockResponse = buildJsonObject {
            putJsonArray("content") {
                add(
                    buildJsonObject {
                        put("type", "tool_use")
                        put("id", "toolu_012345")
                        put("name", "get_weather")
                        putJsonObject("input") {
                            put("city", "Paris")
                            put("units", "celsius")
                        }
                    }
                )
            }
            putJsonObject("usage") {
                put("input_tokens", 100)
                put("output_tokens", 50)
            }
            put("stop_reason", "tool_use")
        }

        // Test parsing logic (this would normally be done inside the client)
        val content = mockResponse["content"]?.jsonArray?.firstOrNull()?.jsonObject

        assertNotNull(content)
        assertEquals("tool_use", content["type"]?.jsonPrimitive?.content)
        assertEquals("toolu_012345", content["id"]?.jsonPrimitive?.content)
        assertEquals("get_weather", content["name"]?.jsonPrimitive?.content)

        val input = content["input"]?.jsonObject
        assertNotNull(input)
        assertEquals("Paris", input["city"]?.jsonPrimitive?.content)
        assertEquals("celsius", input["units"]?.jsonPrimitive?.content)
    }

    @Test
    fun testAnthropicMultipleToolCallsParsing() {
        val mockResponse = buildJsonObject {
            putJsonArray("content") {
                add(
                    buildJsonObject {
                        put("type", "tool_use")
                        put("id", "toolu_001")
                        put("name", "get_weather")
                        putJsonObject("input") {
                            put("city", "London")
                        }
                    }
                )
                add(
                    buildJsonObject {
                        put("type", "tool_use")
                        put("id", "toolu_002")
                        put("name", "calculate")
                        putJsonObject("input") {
                            put("expression", "2 + 2")
                        }
                    }
                )
            }
        }

        val content = mockResponse["content"]?.jsonArray

        assertNotNull(content)
        assertEquals(2, content.size)

        val firstTool = content[0].jsonObject
        assertEquals("get_weather", firstTool["name"]?.jsonPrimitive?.content)
        assertEquals("toolu_001", firstTool["id"]?.jsonPrimitive?.content)

        val secondTool = content[1].jsonObject
        assertEquals("calculate", secondTool["name"]?.jsonPrimitive?.content)
        assertEquals("toolu_002", secondTool["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun testAnthropicMixedTextAndToolResponse() {
        val mockResponse = buildJsonObject {
            putJsonArray("content") {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", "I'll help you with the weather. Let me check that for you.")
                    }
                )
                add(
                    buildJsonObject {
                        put("type", "tool_use")
                        put("id", "toolu_123")
                        put("name", "get_weather")
                        putJsonObject("input") {
                            put("city", "Tokyo")
                        }
                    }
                )
            }
        }

        val content = mockResponse["content"]?.jsonArray

        assertNotNull(content)
        assertEquals(2, content.size)

        val textContent = content[0].jsonObject
        assertEquals("text", textContent["type"]?.jsonPrimitive?.content)
        assertContains(textContent["text"]?.jsonPrimitive?.content ?: "", "I'll help you")

        val toolContent = content[1].jsonObject
        assertEquals("tool_use", toolContent["type"]?.jsonPrimitive?.content)
        assertEquals("get_weather", toolContent["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun testToolChoiceConfiguration() {
        // Test different tool choice configurations
        val autoPrompt = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Auto)) {
            user("Search for something")
        }

        val nonePrompt = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.None)) {
            user("Just respond normally")
        }

        val requiredPrompt = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Required)) {
            user("You must use a tool")
        }

        val namedPrompt = Prompt.build("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Named("search"))) {
            user("Use the search tool")
        }

        // Verify tool choice is properly set (this would be tested in integration)
        assertNotNull(autoPrompt.params.toolChoice)
        assertEquals(LLMParams.ToolChoice.Auto, autoPrompt.params.toolChoice)
        assertEquals(LLMParams.ToolChoice.None, nonePrompt.params.toolChoice)
        assertEquals(LLMParams.ToolChoice.Required, requiredPrompt.params.toolChoice)
        assertTrue(namedPrompt.params.toolChoice is LLMParams.ToolChoice.Named)
        assertEquals("search", (namedPrompt.params.toolChoice as LLMParams.ToolChoice.Named).name)
    }

    @Test
    fun testToolParameterTypes() {
        val complexTool = ToolDescriptor(
            name = "complex_tool",
            description = "A tool with various parameter types",
            requiredParameters = listOf(
                ToolParameterDescriptor("string_param", "A string", ToolParameterType.String),
                ToolParameterDescriptor("int_param", "An integer", ToolParameterType.Integer),
                ToolParameterDescriptor("float_param", "A float", ToolParameterType.Float),
                ToolParameterDescriptor("bool_param", "A boolean", ToolParameterType.Boolean)
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    "enum_param",
                    "An enum",
                    ToolParameterType.Enum(arrayOf("option1", "option2", "option3"))
                ),
                ToolParameterDescriptor(
                    "list_param",
                    "A list of strings",
                    ToolParameterType.List(ToolParameterType.String)
                )
            )
        )

        // Verify parameter types are correctly defined
        assertEquals(4, complexTool.requiredParameters.size)
        assertEquals(2, complexTool.optionalParameters.size)

        val enumParam = complexTool.optionalParameters.find { it.name == "enum_param" }
        assertNotNull(enumParam)
        assertTrue(enumParam.type is ToolParameterType.Enum)

        val listParam = complexTool.optionalParameters.find { it.name == "list_param" }
        assertNotNull(listParam)
        assertTrue(listParam.type is ToolParameterType.List)
    }

    @Test
    fun testToolResultHandling() {
        // Test tool result message creation
        val toolResult = Message.Tool.Result(
            id = "toolu_123",
            tool = "get_weather",
            content = "The weather in Paris is 22°C and sunny",
            metaInfo = RequestMetaInfo(timestamp = Instant.parse("2023-01-01T00:00:00Z"))
        )

        assertEquals("toolu_123", toolResult.id)
        assertEquals("get_weather", toolResult.tool)
        assertContains(toolResult.content, "Paris")
        assertContains(toolResult.content, "22°C")
    }

    @Test
    fun testModelToolCapabilities() {
        // Verify Claude 4 models have the most advanced capabilities
        val claude4Opus = BedrockModels.AnthropicClaude4Opus
        val claude4Sonnet = BedrockModels.AnthropicClaude4Sonnet
        assertTrue(claude4Opus.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude4Opus.capabilities.contains(LLMCapability.ToolChoice))
        assertTrue(claude4Opus.capabilities.contains(LLMCapability.Vision.Image))
        assertTrue(claude4Opus.capabilities.contains(LLMCapability.Schema.JSON.Full))
        assertTrue(claude4Sonnet.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude4Sonnet.capabilities.contains(LLMCapability.ToolChoice))
        assertTrue(claude4Sonnet.capabilities.contains(LLMCapability.Vision.Image))
        assertTrue(claude4Sonnet.capabilities.contains(LLMCapability.Schema.JSON.Full))

        // Verify Claude 3.5 models have comprehensive tool support
        val claude35Sonnet = BedrockModels.AnthropicClaude35SonnetV2
        val claude35Haiku = BedrockModels.AnthropicClaude35Haiku
        assertTrue(claude35Sonnet.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude35Sonnet.capabilities.contains(LLMCapability.ToolChoice))
        assertTrue(claude35Haiku.capabilities.contains(LLMCapability.Tools))
        assertTrue(claude35Haiku.capabilities.contains(LLMCapability.ToolChoice))

        // Verify Nova models don't support tools
        val novaMicro = BedrockModels.AmazonNovaMicro
        assertTrue(!novaMicro.capabilities.contains(LLMCapability.Tools))
    }

    @Test
    fun `can create BedrockLLMClient with moderation guardrails settings`() = runTest {
        val guardrailsSettings = BedrockGuardrailsSettings(
            guardrailIdentifier = "test-guardrail",
            guardrailVersion = "1.0"
        )

        val mockClient = object : BedrockRuntimeClient {
            override suspend fun applyGuardrail(input: ApplyGuardrailRequest): ApplyGuardrailResponse {
                return ApplyGuardrailResponse {
                    action = GuardrailAction.GuardrailIntervened
                    assessments = listOf(
                        GuardrailAssessment {
                            contentPolicy = GuardrailContentPolicyAssessment {
                                filters = listOf(
                                    GuardrailContentFilter {
                                        type = GuardrailContentFilterType.Hate
                                        action = GuardrailContentPolicyAction.None
                                        detected = true
                                        confidence = GuardrailContentFilterConfidence.High
                                    },
                                    GuardrailContentFilter {
                                        type = GuardrailContentFilterType.Sexual
                                        action = GuardrailContentPolicyAction.Blocked
                                        detected = true
                                        confidence = GuardrailContentFilterConfidence.Low
                                    },
                                    GuardrailContentFilter {
                                        type = GuardrailContentFilterType.Misconduct
                                        action = GuardrailContentPolicyAction.None
                                        confidence = GuardrailContentFilterConfidence.Medium
                                        detected = true
                                    }
                                )
                            }
                        }
                    )
                    outputs = emptyList()
                }
            }

            override val config: BedrockRuntimeClient.Config get() = TODO("Not yet implemented")
            override suspend fun converse(input: ConverseRequest): ConverseResponse {
                TODO("Not yet implemented")
            }

            override suspend fun <T> converseStream(
                input: ConverseStreamRequest,
                block: suspend (ConverseStreamResponse) -> T
            ): T {
                TODO("Not yet implemented")
            }

            override suspend fun getAsyncInvoke(input: GetAsyncInvokeRequest): GetAsyncInvokeResponse {
                TODO("Not yet implemented")
            }

            override suspend fun invokeModel(input: InvokeModelRequest): InvokeModelResponse {
                TODO("Not yet implemented")
            }

            override suspend fun <T> invokeModelWithBidirectionalStream(
                input: InvokeModelWithBidirectionalStreamRequest,
                block: suspend (InvokeModelWithBidirectionalStreamResponse) -> T
            ): T {
                TODO("Not yet implemented")
            }

            override suspend fun <T> invokeModelWithResponseStream(
                input: InvokeModelWithResponseStreamRequest,
                block: suspend (InvokeModelWithResponseStreamResponse) -> T
            ): T {
                TODO("Not yet implemented")
            }

            override suspend fun listAsyncInvokes(input: ListAsyncInvokesRequest): ListAsyncInvokesResponse {
                TODO("Not yet implemented")
            }

            override suspend fun startAsyncInvoke(input: StartAsyncInvokeRequest): StartAsyncInvokeResponse {
                TODO("Not yet implemented")
            }

            override fun close() {
                TODO("Not yet implemented")
            }
        }

        val client = BedrockLLMClient(
            mockClient,
            moderationGuardrailsSettings = guardrailsSettings
        )

        val prompt = Prompt.build("test") {
            user("This is a test prompt")
        }
        val model = BedrockModels.AnthropicClaude3Sonnet

        val moderationResult = client.moderate(prompt, model)
        assertEquals(true, moderationResult.isHarmful)
        assertEquals(true, moderationResult.violatesCategory(ModerationCategory.Hate))
        assertEquals(true, moderationResult.violatesCategory(ModerationCategory.Sexual))
        assertEquals(true, moderationResult.violatesCategory(ModerationCategory.Misconduct))
        assertEquals(false, moderationResult.violatesCategory(ModerationCategory.Illicit))
        assertEquals(null, moderationResult.categories[ModerationCategory.Illicit])
    }

    @Test
    fun `moderate method throws exception when moderation guardrails settings are not provided`() = runTest {
        // Create client without moderation guardrails settings
        val client = BedrockLLMClient(
            awsAccessKeyId = "test-key",
            awsSecretAccessKey = "test-secret",
            settings = BedrockClientSettings(region = BedrockRegions.US_EAST_1.regionCode),
            clock = Clock.System
        )

        val prompt = Prompt.build("test") {
            user("This is a test prompt")
        }
        val model = BedrockModels.AnthropicClaude3Sonnet

        // Verify that moderate method throws an exception because moderationGuardrailsSettings wasn't provided
        assertFailsWith<IllegalArgumentException> {
            client.moderate(prompt, model)
        }
    }
}
