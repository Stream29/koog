package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * Tests for the OpenTelemetry feature.
 *
 * These tests verify that spans are created correctly during agent execution
 * and that the structure of spans matches the expected hierarchy.
 */
class OpenTelemetryTest {

    private val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    @Test
    fun `test spans are created during agent execution`() = runTest {
        val testExecutor = TestLLMExecutor(testClock)
        val mockTelemetryExporter = MockSpanExporter()

        val agent = AIAgent(
            executor = testExecutor,
            systemPrompt = "You are a helpful assistant.",
            llmModel = OpenAIModels.Chat.GPT4o,
            installFeatures = {
                install(OpenTelemetry) {
                    otelServiceName = "test-service"
                    addSpanExporters(mockTelemetryExporter)
                }
            }
        )

        agent.run("Hello, how are you?")
        val collectedSpans = mockTelemetryExporter.collectedSpans
        assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

        val expectedAgentSpans = listOf(
            ""
        )

        val actualAgentSpans = mockTelemetryExporter.collectedSpans.map { it.name }

        assertContentEquals(expectedAgentSpans, actualAgentSpans)
    }
}

/**
 * A mock span exporter that captures spans created by the OpenTelemetry feature.
 * This allows us to inject a MockTracer into the OpenTelemetry feature.
 */
class MockSpanExporter() : SpanExporter {

    val collectedSpans = mutableListOf<SpanData>()

    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        collectedSpans.addAll(spans)
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }
}

class TestLLMExecutor(val clock: Clock) : PromptExecutor {
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        return listOf(handlePrompt(prompt))
    }

    override suspend fun executeStreaming(
        prompt: Prompt,
        model: LLModel
    ): Flow<String> {
        return flow {
            emit(handlePrompt(prompt).content)
        }
    }

    private fun handlePrompt(prompt: Prompt): Message.Response {
        return Message.Assistant(
            "Default test response",
            metaInfo = ResponseMetaInfo.create(clock)
        )
    }
}
