package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
                "node.nodeCallLLM",
                "llm.chat",
                "node.__start__",
            )

            val actualAgentSpans = collectedSpans.map { it.name }

            assertEquals(expectedAgentSpans.size, actualAgentSpans.size)
            assertContentEquals(expectedAgentSpans, actualAgentSpans)

            // Verify span attributes
            collectedSpans.forEach { span ->
                verifySpanAttributes(span)
            }

            // Verify span hierarchy
            verifySpanHierarchy(collectedSpans)

            // Verify events captured during execution
            verifySpanEvents(collectedSpans)
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
                "node.nodeCallLLM",
                "llm.chat",
                "node.__start__",
                "run.${firstRunId}",
                "node.nodeCallLLM",
                "llm.chat",
                "node.__start__",
            )

            val actualSpans = mockExporter.collectedSpans.map { it.name }

            assertEquals(expectedSpans.size, actualSpans.size)
            assertContentEquals(expectedSpans, actualSpans)

            // Verify span attributes
            mockExporter.collectedSpans.forEach { span ->
                verifySpanAttributes(span)
            }

            // Verify span hierarchy
            verifySpanHierarchy(mockExporter.collectedSpans)

            // Verify events captured during execution
            verifySpanEvents(mockExporter.collectedSpans)

            // Verify that spans from different runs have different parent-child relationships
            val firstRunSpans = mockExporter.collectedSpans.filter { it.name.contains(firstRunId) }
            val secondRunSpans = mockExporter.collectedSpans.filter { it.name.contains(secondRunId) }

            assertTrue(firstRunSpans.isNotEmpty(), "First run should have spans")
            assertTrue(secondRunSpans.isNotEmpty(), "Second run should have spans")

            // Verify that spans from different runs don't have parent-child relationships between them
            firstRunSpans.forEach { firstRunSpan ->
                secondRunSpans.forEach { secondRunSpan ->
                    if (firstRunSpan.parentSpanId != SpanId.getInvalid()) {
                        assertTrue(secondRunSpans.none { it.spanContext.spanId == firstRunSpan.parentSpanId }, 
                            "Span from first run should not have parent from second run")
                    }
                    if (secondRunSpan.parentSpanId != SpanId.getInvalid()) {
                        assertTrue(firstRunSpans.none { it.spanContext.spanId == secondRunSpan.parentSpanId }, 
                            "Span from second run should not have parent from first run")
                    }
                }
            }
        }
    }

    //region Private Methods

    private fun verifySpanAttributes(span: SpanData) {
        // Verify that span has attributes
        val attributes = span.attributes
        assertNotNull(attributes, "Span should have attributes")

        // All spans should have status
        assertNotNull(span.status, "Span should have status")
    }
    
    private fun verifySpanHierarchy(spans: List<SpanData>) {
        // Create a map of span ID to span
        val spanMap = spans.associateBy { it.spanContext.spanId }

        // Check parent-child relationships
        spans.forEach { span ->
            val parentSpanId = span.parentSpanId
            if (parentSpanId != SpanId.getInvalid()) {
                // If span has a parent, the parent should be in the collected spans
                val parentSpan = spanMap[parentSpanId]
                assertNotNull(parentSpan, "Parent span should be in collected spans")

                // Verify parent-child relationship based on span names
                when {
                    span.name.startsWith("run.") -> {
                        assertTrue(parentSpan.name.startsWith("agent."), "Run span should have agent span as parent")
                    }
                    span.name.startsWith("node.") -> {
                        assertTrue(parentSpan.name.startsWith("run."), "Node span should have run span as parent")
                    }
                    span.name.startsWith("llm.") -> {
                        assertTrue(parentSpan.name.startsWith("node."), "LLM span should have node span as parent")
                    }
                }
            }
        }
    }
    
    private fun verifySpanEvents(spans: List<SpanData>) {
        // Check that at least some spans have events
        val spansWithEvents = spans.filter { it.events.isNotEmpty() }
        assertTrue(spansWithEvents.isNotEmpty(), "At least some spans should have events")

        // Verify events in spans
        spansWithEvents.forEach { span ->
            span.events.forEach { event ->
                assertNotNull(event.name, "Event should have a name")
                assertTrue(event.name.isNotEmpty(), "Event name should not be empty")

                // Verify event attributes
                val attributes = event.attributes
                assertNotNull(attributes, "Event should have attributes")
            }
        }
    }

    //endregion Private Methods
}
