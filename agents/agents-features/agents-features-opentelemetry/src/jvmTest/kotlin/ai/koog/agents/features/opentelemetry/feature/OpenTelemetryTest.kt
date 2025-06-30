package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
    fun `test spans are created during agent execution`() = runBlocking {
        val testExecutor = MockLLMExecutor(testClock)
        MockSpanExporter().use { mockExporter ->

            val agent = AIAgent(
                id = "test-agent-id",
                executor = testExecutor,
                systemPrompt = "You are a helpful assistant.",
                llmModel = OpenAIModels.Chat.GPT4o,
                installFeatures = {
                    install(OpenTelemetry) {
                        addSpanExporter(mockExporter)
                    }
                }
            )

            agent.run("Hello, how are you?")

            val collectedSpans = mockExporter.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            agent.close()

            val expectedAgentSpans = listOf(
                "agent.test-agent-id",
                "run.${mockExporter.runId}",
                "node.sendInput",
                "llm.chat",
                "node.__start__",
            )

            val actualAgentSpans = collectedSpans.map { it.name }

            assertEquals(expectedAgentSpans.size, actualAgentSpans.size)
            assertContentEquals(expectedAgentSpans, actualAgentSpans)
        }
    }

    @Test
    fun `test multiple agent runs create separate spans`() = runBlocking {
        val testExecutor = MockLLMExecutor(testClock)
        MockSpanExporter().use { mockExporter ->

            val agent = AIAgent(
                id = "test-agent-id",
                executor = testExecutor,
                systemPrompt = "You are a helpful assistant.",
                llmModel = OpenAIModels.Chat.GPT4o,
                installFeatures = {
                    install(OpenTelemetry) {
                        addSpanExporter(mockExporter)
                    }
                }
            )

            // Run agent multiple times

            agent.run("First message")
            val firstRunId = mockExporter.runId

            agent.run("Second message")
            val secondRunId = mockExporter.runId

            agent.close()

            val expectedSpans = listOf(
                "agent.test-agent-id",
                "run.${secondRunId}",
                "node.sendInput",
                "llm.chat",
                "node.__start__",
                "run.${firstRunId}",
                "node.sendInput",
                "llm.chat",
                "node.__start__",
            )

            val actualSpans = mockExporter.collectedSpans.map { it.name }

            assertEquals(expectedSpans.size, actualSpans.size)
            assertContentEquals(expectedSpans, actualSpans)
        }
    }
}
