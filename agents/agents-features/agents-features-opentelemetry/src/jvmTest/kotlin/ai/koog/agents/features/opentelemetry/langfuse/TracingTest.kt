package ai.koog.agents.features.opentelemetry.langfuse

import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeUpdatePrompt
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.createAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.agents.utils.use
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.lang.System
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.assertTrue

/**
 * Test assumes manual traces inspection using Langfuse.
 *
 * TODO: convert to general OTEL test with InMemorySpanExporter
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
    }

    @Test
    fun testSubsequentLLMCalls() = runBlocking {
        val strategy = strategy("tracing-test-strategy") {
            val setPrompt by nodeUpdatePrompt("Set prompt") {
                system("System 1")
                user("User 1")
            }
            val updatePrompt by nodeUpdatePrompt("Update prompt") {
                system("System 2")
                user("User 2")
            }

            val llmRequest0 by nodeLLMRequest("LLM Request 1", allowToolCalls = false)
            val llmRequest1 by nodeLLMRequest("LLM Request 2", allowToolCalls = false)

            edge(nodeStart forwardTo setPrompt)
            edge(setPrompt forwardTo llmRequest0)
            edge(llmRequest0 forwardTo updatePrompt transformed { input -> })
            edge(updatePrompt forwardTo llmRequest1 transformed { input -> "" })
            edge(llmRequest1 forwardTo nodeFinish transformed { input -> input.content })
        }

        runAgentWithStrategy(strategy)
    }

    private suspend fun runAgentWithStrategy(strategy: AIAgentStrategy<String, String>) {
        val userPrompt = "User prompt message"
        val agentId = "test-agent-id"
        val promptId = "test-prompt-id"
        val testClock = Clock.System
        val model = OpenAIModels.Chat.GPT4o
        val temperature = 0.4

        val mockResponse = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

        val mockExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer(mockResponse) onRequestEquals userPrompt
        }

        val agent = createAgent(
            agentId = agentId,
            strategy = strategy,
            promptId = promptId,
            promptExecutor = mockExecutor,
            model = model,
            clock = testClock,
            temperature = temperature
        ) {
            install(OpenTelemetry) {
                addSpanExporter(
                    createLangfuseSpanExporter(
                        System.getenv()["LANGFUSE_HOST"]
                            ?: throw IllegalArgumentException("LANGFUSE_PUBLIC_KEY is not set"),
                        System.getenv()["LANGFUSE_PUBLIC_KEY"]
                            ?: throw IllegalArgumentException("LANGFUSE_PUBLIC_KEY is not set"),
                        System.getenv()["LANGFUSE_SECRET_KEY"]
                            ?: throw IllegalArgumentException("LANGFUSE_SECRET_KEY is not set"),
                    )
                )
                addSpanExporter(
                    createWeaveSpanExporter(
                        "https://trace.wandb.ai",
                        System.getenv()["WEAVE_ENTITY"] ?: throw IllegalArgumentException("WEAVE_ENTITY is not set"),
                        System.getenv()["WEAVE_PROJECT_NAME"] ?: "koog-tracing",
                        System.getenv()["WEAVE_API_KEY"] ?: throw IllegalArgumentException("WEAVE_API_KEY is not set")
                    )
                )
                addSpanExporter(inMemorySpanExporter)
            }
        }

        agent.use {
            it.run(userPrompt)
        }
    }
}

fun createLangfuseSpanExporter(
    langfuseUrl: String,
    langfusePublicKey: String,
    langfuseSecretKey: String,
): SpanExporter {
    val credentials = "$langfusePublicKey:$langfuseSecretKey"
    val auth = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))

    return OtlpHttpSpanExporter.builder()
        .setTimeout(30, TimeUnit.SECONDS)
        .setEndpoint("$langfuseUrl/api/public/otel/v1/traces")
        .addHeader("Authorization", "Basic $auth")
        .build()
}

private fun createWeaveSpanExporter(
    weaveOtelUrl: String,
    entity: String,
    projectName: String,
    apiKey: String,
): SpanExporter {
    val auth = Base64.getEncoder().encodeToString("api:$apiKey".toByteArray(Charsets.UTF_8))

    return OtlpHttpSpanExporter.builder()
        .setTimeout(30, TimeUnit.SECONDS)
        .setEndpoint("$weaveOtelUrl/otel/v1/traces")
        .addHeader("project_id", "$entity/$projectName")
        .addHeader("Authorization", "Basic $auth")
        .build()
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

    fun analyzeSpans(): List<SpanData> {
        tracerProvider.forceFlush().join(30, TimeUnit.SECONDS)
        return spanExporter.finishedSpanItems.mapNotNull { it }
    }
}

fun initOpenTelemetry(): Tracing {
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

data class Tracing(
    val tracerProvider: SdkTracerProvider,
    val spanExporter: SpanExporter
)