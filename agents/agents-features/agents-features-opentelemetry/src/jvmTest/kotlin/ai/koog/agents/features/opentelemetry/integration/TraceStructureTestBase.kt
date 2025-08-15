package ai.koog.agents.features.opentelemetry.integration

import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI
import ai.koog.agents.features.opentelemetry.assertMapsEqual
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.agents.features.opentelemetry.mock.TestGetWeatherTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.agents.utils.use
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class TraceStructureTestBase(private val openTelemetryConfigurator: OpenTelemetryConfig.() -> Unit) {

    @Test
    fun testSingleLLMCall() = runBlocking {
        MockSpanExporter().use { mockSpanExporter ->

            val strategy = strategy("single-llm-call-strategy") {
                val llmRequest by nodeLLMRequest("llm-call")

                edge(nodeStart forwardTo llmRequest)
                edge(llmRequest forwardTo nodeFinish onAssistantMessage { true })
            }

            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4
            val systemPrompt = "You are the application that predicts weather"
            val userPrompt = "What's the weather in Paris?"
            val mockResponse = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

            val promptExecutor = getMockExecutor {
                mockLLMAnswer(mockResponse) onRequestEquals userPrompt
            }

            runAgentWithStrategy(
                strategy = strategy,
                promptExecutor = promptExecutor,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                model = model,
                temperature = temperature,
                spanExporter = mockSpanExporter,
            )

            val actualSpans = mockSpanExporter.collectedSpans

            assertTrue { actualSpans.filter { it.name.startsWith("run.") }.size == 1 }
            assertTrue { actualSpans.filter { it.name == "node.__start__" }.size == 1 }
            assertTrue { actualSpans.filter { it.name == "node.llm-call" }.size == 1 }
            assertTrue { actualSpans.filter { it.name == "llm.test-prompt-id" }.size == 1 }

            val runNode = actualSpans.first { it.name.startsWith("run.") }
            val startNode = actualSpans.first { it.name == "node.__start__" }
            val llmNode = actualSpans.first { it.name == "node.llm-call" }
            val llmGeneration = actualSpans.first { it.name == "llm.test-prompt-id" }

            assertTrue { runNode.spanId == startNode.parentSpanId }
            assertTrue { runNode.spanId == llmNode.parentSpanId }
            assertTrue { llmNode.spanId == llmGeneration.parentSpanId }

            val actualSpanAttributes = llmGeneration.attributes.asMap()
                .map { (key, value) -> key.key to value.toString() }
                .toMap()

            // Assert expected LLM Call Span (IntentSpan) attributes for Langfuse/Weave

            val expectedAttributes = mapOf(
                // General attributes
                "gen_ai.system" to model.provider.id,
                "gen_ai.request.model" to model.id,
                "gen_ai.request.temperature" to "0.4",
                "gen_ai.conversation.id" to mockSpanExporter.lastRunId,
                "gen_ai.operation.name" to "chat",

                // Langfuse/Weave specific attributes
                "gen_ai.prompt.0.role" to "system",
                "gen_ai.prompt.0.content" to systemPrompt,
                "gen_ai.prompt.1.role" to "user",
                "gen_ai.prompt.1.content" to userPrompt,
                "gen_ai.completion.0.role" to "assistant",
                "gen_ai.completion.0.content" to mockResponse,
            )

            assertEquals(expectedAttributes.size, actualSpanAttributes.size)
            assertMapsEqual(expectedAttributes, actualSpanAttributes)
        }
    }

    @Test
    fun testLLMCallToolCallLLMCall() = runBlocking {
        MockSpanExporter().use { mockSpanExporter ->
            val strategy = strategy("llm-tool-llm-strategy") {
                val llmRequest by nodeLLMRequest("LLM Request", allowToolCalls = true)
                val executeTool by nodeExecuteTool("Execute Tool")
                val sendToolResult by nodeLLMSendToolResult("Send Tool Result")

                edge(nodeStart forwardTo llmRequest)
                edge(llmRequest forwardTo executeTool onToolCall { true })
                edge(executeTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            }

            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4
            val systemPrompt = "You are the application that predicts weather"
            val userPrompt = "What's the weather in Paris?"
            val toolCallArgs = TestGetWeatherTool.Args("Paris")
            val toolResponse = "rainy, 57°F"
            val llmResponse = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

            val mockExecutor = getMockExecutor {
                mockLLMToolCall(TestGetWeatherTool, toolCallArgs) onRequestEquals userPrompt
                mockLLMAnswer(llmResponse) onRequestContains toolResponse
            }

            val toolRegistry = ToolRegistry {
                tool(TestGetWeatherTool)
            }

            runAgentWithStrategy(
                strategy = strategy,
                promptExecutor = mockExecutor,
                toolRegistry = toolRegistry,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                model = model,
                temperature = temperature,
                spanExporter = mockSpanExporter
            )

            val actualSpans = mockSpanExporter.collectedSpans

            assertTrue { actualSpans.filter { it.name == "tool.Get whether" }.size == 1 }

            val runNode = actualSpans.first { it.name.startsWith("run.") }
            val startNode = actualSpans.first { it.name == "node.__start__" }
            val llmRequestNode = actualSpans.first { it.name == "node.LLM Request" }
            val executeToolNode = actualSpans.first { it.name == "node.Execute Tool" }
            val sendToolResultNode = actualSpans.first { it.name == "node.Send Tool Result" }
            val toolCallSpan = actualSpans.first { it.name == "tool.Get whether" }
            val llmSpans = actualSpans.filter { it.name == "llm.test-prompt-id" }

            // All nodes should have runNode as parent
            assertTrue { runNode.spanId == startNode.parentSpanId }
            assertTrue { runNode.spanId == llmRequestNode.parentSpanId }
            assertTrue { runNode.spanId == executeToolNode.parentSpanId }
            assertTrue { runNode.spanId == sendToolResultNode.parentSpanId }

            // Tool span should have executed tool node as parent
            assertTrue { executeToolNode.spanId == toolCallSpan.parentSpanId }

            // LLM span
            val actualLLMSpansAttributes = llmSpans.map { span ->
                span.attributes.asMap()
                    .map { (key, value) -> key.key to value.toString() }
                    .toMap()
            }

            /**
             * {InternalAttributeKeyImpl@6774} "gen_ai.system" -> "openai"
             * {InternalAttributeKeyImpl@6776} "gen_ai.request.temperature" -> {Double@6777} 0.0
             * {InternalAttributeKeyImpl@6778} "gen_ai.request.model" -> "gpt-4o"
             * {InternalAttributeKeyImpl@6780} "gen_ai.conversation.id" -> "33c8d520-17e1-424d-b5cd-1250053d7888"
             * {InternalAttributeKeyImpl@6782} "gen_ai.prompt.0.content" -> "What's the weather in Paris?"
             * {InternalAttributeKeyImpl@6784} "gen_ai.operation.name" -> "chat"
             * {InternalAttributeKeyImpl@6786} "gen_ai.completion.1.role" -> "assistant"
             * {InternalAttributeKeyImpl@6788} "gen_ai.prompt.0.role" -> "user"
             * {InternalAttributeKeyImpl@6790} "gen_ai.completion.1.content" -> "The weather in Paris is rainy and overcast, with temperatures around 57°F"
             *
             * {InternalAttributeKeyImpl@6806} "gen_ai.system" -> "openai"
             * {InternalAttributeKeyImpl@6807} "gen_ai.request.temperature" -> {Double@6808} 0.0
             * {InternalAttributeKeyImpl@6809} "gen_ai.request.model" -> "gpt-4o"
             * {InternalAttributeKeyImpl@6810} "gen_ai.conversation.id" -> "33c8d520-17e1-424d-b5cd-1250053d7888"
             * {InternalAttributeKeyImpl@6811} "gen_ai.prompt.0.content" -> "What's the weather in Paris?"
             * {InternalAttributeKeyImpl@6812} "gen_ai.operation.name" -> "chat"
             * {InternalAttributeKeyImpl@6813} "gen_ai.prompt.0.role" -> "user"
             */
            val expectedAttributes = listOf(
                mapOf(
                    "gen_ai.system" to model.provider.id,
                    "gen_ai.request.temperature" to temperature.toString(),
                    "gen_ai.request.model" to model.id,
                    "gen_ai.conversation.id" to mockSpanExporter.lastRunId,
                    "gen_ai.operation.name" to "chat",

                    "gen_ai.prompt.0.role" to "system",
                    "gen_ai.prompt.0.content" to systemPrompt,
                    "gen_ai.prompt.1.role" to "user",
                    "gen_ai.prompt.1.content" to userPrompt,
                    "gen_ai.completion.0.role" to "assistant",
                    "gen_ai.completion.0.content" to mockResponse,
                ),
                mapOf(
                    "" to ""
                ),
            )

            assertTrue { llmSpans.any { it.parentSpanId == llmRequestNode.spanId } }
            assertTrue { llmSpans.any { it.parentSpanId == sendToolResultNode.spanId } }
        }
    }

    @Test
    fun testMultipleToolCalls() = runBlocking {
        MockSpanExporter().use { mockSpanExporter ->
            val strategy = strategy("multiple-tool-calls-strategy") {
                val llmRequest by nodeLLMRequest("Initial LLM Request", allowToolCalls = true)
                val executeTool1 by nodeExecuteTool("Execute Tool 1")
                val sendToolResult1 by nodeLLMSendToolResult("Send Tool Result 1")
                val executeTool2 by nodeExecuteTool("Execute Tool 2")
                val sendToolResult2 by nodeLLMSendToolResult("Send Tool Result 2")

                edge(nodeStart forwardTo llmRequest)
                edge(llmRequest forwardTo executeTool1 onToolCall { true })
                edge(executeTool1 forwardTo sendToolResult1)
                edge(sendToolResult1 forwardTo executeTool2 onToolCall { true })
                edge(executeTool2 forwardTo sendToolResult2)
                edge(sendToolResult2 forwardTo nodeFinish transformed { input -> input.content })
            }

            val userPrompt = "What's the weather in Paris and London?"
            val toolCallArgs1 = TestGetWeatherTool.Args("Paris")
            val toolResponse1 = "rainy, 57°F"
            val toolCallArgs2 = TestGetWeatherTool.Args("London")
            val toolResponse2 = "cloudy, 62°F"
            val finalResponse = "The weather in Paris is rainy (57°F) and in London it's cloudy (62°F)"

            val mockExecutor = getMockExecutor {
                mockLLMToolCall(TestGetWeatherTool, toolCallArgs1) onRequestEquals userPrompt
                mockLLMToolCall(TestGetWeatherTool, toolCallArgs2) onRequestContains toolResponse1
                mockLLMAnswer(finalResponse) onRequestContains toolResponse2
            }

            val toolRegistry = ToolRegistry {
                tool(TestGetWeatherTool)
            }

            runAgentWithStrategy(
                strategy = strategy,
                promptExecutor = mockExecutor,
                toolRegistry = toolRegistry,
                userPrompt = userPrompt,
                spanExporter = mockSpanExporter
            )

            val actualSpans = mockSpanExporter.collectedSpans

            val executeTool1Node = actualSpans.first { it.name == "node.Execute Tool 1" }
            val executeTool2Node = actualSpans.first { it.name == "node.Execute Tool 2" }
            val toolNodes = actualSpans.filter { it.name == "tool.Get whether" }

            assertTrue { toolNodes.any { it.parentSpanId == executeTool1Node.spanId } }
            assertTrue { toolNodes.any { it.parentSpanId == executeTool2Node.spanId } }
            assertEquals(2, toolNodes.size, "Should have exactly two tool nodes for the two weather requests")
        }
    }

    /**
     * Runs an agent with the given strategy and verifies the spans.
     */
    private suspend fun runAgentWithStrategy(
        strategy: AIAgentStrategy<String, String>,
        systemPrompt: String? = null,
        userPrompt: String? = null,
        promptExecutor: PromptExecutor? = null,
        toolRegistry: ToolRegistry? = null,
        model: LLModel? = null,
        temperature: Double? = null,
        spanExporter: SpanExporter? = null,
        verbose: Boolean = true
    ) {
        val agentId = "test-agent-id"
        val promptId = "test-prompt-id"
        val testClock = Clock.System

        OpenTelemetryTestAPI.createAgent(
            agentId = agentId,
            strategy = strategy,
            promptId = promptId,
            promptExecutor = promptExecutor,
            model = model,
            clock = testClock,
            temperature = temperature,
            systemPrompt = systemPrompt,
            toolRegistry = toolRegistry,
        ) {
            install(OpenTelemetry.Feature) {
                spanExporter?.let { exporter -> addSpanExporter(exporter) }
                setVerbose(verbose)
                openTelemetryConfigurator()
            }
        }.use { agent ->
            agent.run(userPrompt ?: "User prompt message")
        }
    }
}
