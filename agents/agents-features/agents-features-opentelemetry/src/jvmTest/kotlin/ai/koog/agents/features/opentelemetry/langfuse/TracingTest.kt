package ai.koog.agents.features.opentelemetry.langfuse

import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.createAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integrations.addLangfuseExporter
import ai.koog.agents.features.opentelemetry.integrations.addWeaveExporter
import ai.koog.agents.features.opentelemetry.mock.TestGetWeatherTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.agents.utils.use
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test traces OpenTelemetry structure conformance to Langfuse and Weave data models.
 */
class TracingTest {
    val inMemorySpanExporter: InMemorySpanExporter = InMemorySpanExporter.create()

    @AfterTest
    fun cleanSpans() {
        inMemorySpanExporter.reset()
    }

    @Test
    fun testSingleLLMCall() = runBlocking {
        val strategy = strategy("single-llm-call-strategy") {
            val llmRequest by nodeLLMRequest("llm-call")

            edge(nodeStart forwardTo llmRequest)
            edge(llmRequest forwardTo nodeFinish onAssistantMessage { true })
        }

        runAgentWithStrategy(strategy)

        val spans = inMemorySpanExporter.finishedSpanItems

        assertTrue { spans.filter { it.name.startsWith("run.") }.size == 1 }
        assertTrue { spans.filter { it.name == "node.__start__" }.size == 1 }
        assertTrue { spans.filter { it.name == "node.llm-call" }.size == 1 }
        assertTrue { spans.filter { it.name == "llm.test-prompt-id" }.size == 1 }

        val runNode = spans.first { it.name.startsWith("run.") }
        val startNode = spans.first { it.name == "node.__start__" }
        val llmNode = spans.first { it.name == "node.llm-call" }
        val llmGeneration = spans.first { it.name == "llm.test-prompt-id" }

        assertTrue { runNode.spanId == startNode.parentSpanId }
        assertTrue { runNode.spanId == llmNode.parentSpanId }
        assertTrue { llmNode.spanId == llmGeneration.parentSpanId }

        val events = llmGeneration.events

        assertEquals(1, llmGeneration.events.count { it.name == "gen_ai.system.message" }, "System message is traced")
        assertEquals(1, llmGeneration.events.count { it.name == "gen_ai.user.message" }, "User message is traced")
        assertEquals(1, llmGeneration.events.count { it.name == "gen_ai.assistant.message" }, "Assistant message is traced")

        assertTrue("Exactly three messages are traced") { events.size == 3 }
    }

    @Test
    fun testLLMCallToolCallLLMCall() = runBlocking {
        val strategy = strategy("llm-tool-llm-strategy") {
            val llmRequest by nodeLLMRequest("LLM Request", allowToolCalls = true)
            val executeTool by nodeExecuteTool("Execute Tool")
            val sendToolResult by nodeLLMSendToolResult("Send Tool Result")

            edge(nodeStart forwardTo llmRequest)
            edge(llmRequest forwardTo executeTool onToolCall { true })
            edge(executeTool forwardTo sendToolResult)
            edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
        }

        val userPrompt = "What's the weather in Paris?"
        val toolCallArgs = TestGetWeatherTool.Args("Paris")
        val toolResponse = "rainy, 57°F"
        val finalResponse = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

        val mockExecutor = getMockExecutor {
            mockLLMToolCall(TestGetWeatherTool, toolCallArgs) onRequestEquals userPrompt
            mockLLMAnswer(finalResponse) onRequestContains toolResponse
        }

        runAgentWithStrategy(strategy, userPrompt, mockExecutor)

        val spans = inMemorySpanExporter.finishedSpanItems

        assertTrue { spans.filter { it.name == "tool.Get whether" }.size == 1 }

        val runNode = spans.first { it.name.startsWith("run.") }
        val startNode = spans.first { it.name == "node.__start__" }
        val llmRequestNode = spans.first { it.name == "node.LLM Request" }
        val executeToolNode = spans.first { it.name == "node.Execute Tool" }
        val sendToolResultNode = spans.first { it.name == "node.Send Tool Result" }
        val toolNode = spans.first { it.name == "tool.Get whether" }

        // All nodes should have runNode as parent
        assertTrue { runNode.spanId == startNode.parentSpanId }
        assertTrue { runNode.spanId == llmRequestNode.parentSpanId }
        assertTrue { runNode.spanId == executeToolNode.parentSpanId }
        assertTrue { runNode.spanId == sendToolResultNode.parentSpanId }

        // Tool span should have execute tool node as parent
        assertTrue { executeToolNode.spanId == toolNode.parentSpanId }

        val llmSpans = spans.filter { it.name == "llm.test-prompt-id" }
        assertTrue { llmSpans.any { it.parentSpanId == llmRequestNode.spanId } }
        assertTrue { llmSpans.any { it.parentSpanId == sendToolResultNode.spanId } }
    }

    @Test
    fun testMultipleToolCalls() = runBlocking {
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

        runAgentWithStrategy(strategy, userPrompt, mockExecutor)

        val spans = inMemorySpanExporter.finishedSpanItems

        val executeTool1Node = spans.first { it.name == "node.Execute Tool 1" }
        val executeTool2Node = spans.first { it.name == "node.Execute Tool 2" }
        val toolNodes = spans.filter { it.name == "tool.Get whether" }

        assertTrue { toolNodes.any { it.parentSpanId == executeTool1Node.spanId } }
        assertTrue { toolNodes.any { it.parentSpanId == executeTool2Node.spanId } }
        assertEquals(2, toolNodes.size, "Should have exactly two tool nodes for the two weather requests")
    }

    /**
     * Runs an agent with the given strategy and verifies the spans.
     *
     * @param strategy The strategy to run
     * @param userPrompt The user prompt to send to the agent
     * @param mockExecutor The mock executor to use for testing
     * @param toolRegistry The tool registry to use for the agent. If not provided, a default one with TestGetWeatherTool will be created.
     *                     This is necessary because the tests use TestGetWeatherTool, which needs to be registered.
     */
    private suspend fun runAgentWithStrategy(
        strategy: AIAgentStrategy<String, String>,
        userPrompt: String = "User prompt message",
        mockExecutor: PromptExecutor = getMockExecutor {
            mockLLMAnswer("The weather in Paris is rainy and overcast, with temperatures around 57°F") onRequestEquals userPrompt
        },
        toolRegistry: ToolRegistry = ToolRegistry {
            tool(TestGetWeatherTool)
        },
    ) {
        val agentId = "test-agent-id"
        val promptId = "test-prompt-id"
        val testClock = Clock.System
        val model = OpenAIModels.Chat.GPT4o
        val temperature = 0.4

        val agent = createAgent(
            agentId = agentId,
            strategy = strategy,
            promptId = promptId,
            promptExecutor = mockExecutor,
            model = model,
            clock = testClock,
            temperature = temperature,
            systemPrompt = "System message",
            toolRegistry = toolRegistry,
        ) {
            install(OpenTelemetry) {
                addLangfuseExporter(langfuseUrl = "https://langfuse.labs.jb.gg/")
                addWeaveExporter()
                addSpanExporter(inMemorySpanExporter)
                setVerbose(true)
            }
        }

        agent.use {
            it.run(userPrompt)
        }
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseOpenTelemetryTracingTest {
    internal lateinit var tracerProvider: SdkTracerProvider
    internal lateinit var spanExporter: InMemorySpanExporter
    internal lateinit var tracer: Tracer

    @BeforeAll
    fun setupTelemetry() {
        val tracing = initOpenTelemetry()

        tracerProvider = tracing.tracerProvider
        spanExporter = tracing.spanExporter as InMemorySpanExporter
        tracer = GlobalOpenTelemetry.getTracer("koog.tracer")
    }

    @AfterTest
    fun cleanSpans() {
        spanExporter.reset()
    }

    @AfterAll
    fun shutdownTelemetry() {
        tracerProvider.apply {
            forceFlush().join(1, TimeUnit.SECONDS)
            shutdown().join(1, TimeUnit.SECONDS)
        }
    }
}

private fun initOpenTelemetry(): Tracing {
    val resource = Resource.getDefault()
        .merge(
            Resource.create(
                Attributes.of(AttributeKey.stringKey("service.name"), "ai-development-kit")
            )
        )

    val spanExporter = InMemorySpanExporter.create()
    val batchProcessor = BatchSpanProcessor.builder(spanExporter)
        .setScheduleDelay(Duration.ofMillis(100))
        .setMaxExportBatchSize(512)
        .setMaxQueueSize(2048)
        .build()


    val tracerProvider = SdkTracerProvider.builder()
        .setResource(resource)
        .addSpanProcessor(batchProcessor)
        .build()

    val openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .buildAndRegisterGlobal()

    val sdk = openTelemetry
    Runtime.getRuntime().addShutdownHook(Thread {
        sdk.sdkTracerProvider.shutdown()
    })

    return Tracing(tracerProvider, spanExporter)
}

private data class Tracing(
    val tracerProvider: SdkTracerProvider,
    val spanExporter: SpanExporter
)