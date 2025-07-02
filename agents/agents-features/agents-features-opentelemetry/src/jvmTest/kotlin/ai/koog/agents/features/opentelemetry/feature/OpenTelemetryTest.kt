package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanId
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

            // Define expected attributes for each span type
            val expectedAttributes = mapOf(
                "agent" to mapOf(
                    "operation.name" to "create_agent"
                ),
                "run" to mapOf(
                    "operation.name" to "invoke_agent"
                ),
                "node" to mapOf(
                    // No specific attributes to check for node spans
                ),
                "llm" to mapOf(
                    "operation.name" to "chat"
                )
            )

            // Verify span attributes
            collectedSpans.forEach { span ->
                verifySpanAttributes(expectedAttributes, span)
            }

            // Define expected parent-child relationships
            val expectedHierarchy = mapOf(
                "run" to "agent",
                "node" to "run",
                "llm" to "node"
            )

            // Verify span hierarchy
            verifySpanHierarchy(expectedHierarchy, collectedSpans)

            // Define expected event attributes
            val expectedEvents = mapOf(
                "user.message" to listOf("gen_ai.system", "content"),
                "choice" to listOf("gen_ai.system", "content")
            )

            // Verify events captured during execution
            verifySpanEvents(expectedEvents, collectedSpans)
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

            // Define expected attributes for each span type
            val expectedAttributes = mapOf(
                "agent" to mapOf(
                    "operation.name" to "create_agent"
                ),
                "run" to mapOf(
                    "operation.name" to "invoke_agent"
                ),
                "node" to mapOf(
                    // No specific attributes to check for node spans
                ),
                "llm" to mapOf(
                    "operation.name" to "chat"
                )
            )

            // Verify span attributes
            mockExporter.collectedSpans.forEach { span ->
                verifySpanAttributes(expectedAttributes, span)
            }

            // Define expected parent-child relationships
            val expectedHierarchy = mapOf(
                "run" to "agent",
                "node" to "run",
                "llm" to "node"
            )

            // Verify span hierarchy
            verifySpanHierarchy(expectedHierarchy, mockExporter.collectedSpans)

            // Define expected event attributes
            val expectedEvents = mapOf(
                "user.message" to listOf("gen_ai.system", "content"),
                "choice" to listOf("gen_ai.system", "content")
            )

            // Verify events captured during execution
            verifySpanEvents(expectedEvents, mockExporter.collectedSpans)

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

    private fun verifySpanAttributes(expected: Map<String, Map<String, Any>>, actual: SpanData) {
        // Verify that span has attributes
        val attributes = actual.attributes
        assertNotNull(attributes, "Span should have attributes")

        // All spans should have status
        assertNotNull(actual.status, "Span should have status")

        // Get expected attributes for this span type
        val spanType = when {
            actual.name.startsWith("agent.") -> "agent"
            actual.name.startsWith("run.") -> "run"
            actual.name.startsWith("node.") -> "node"
            actual.name.startsWith("llm.") -> "llm"
            else -> throw IllegalArgumentException("Unknown span type: ${actual.name}")
        }

        val expectedAttributes = expected[spanType] ?: throw IllegalArgumentException("No expected attributes for span type: $spanType")

        // Verify specific attributes based on span name
        when (spanType) {
            "agent" -> {
                // Agent spans should have gen_ai.operation.name = create_agent and gen_ai.agent.id
                val operationName = attributes.get(AttributeKey.stringKey("gen_ai.operation.name"))
                assertEquals(expectedAttributes["operation.name"], operationName, "Agent span should have operation.name = ${expectedAttributes["operation.name"]}")

                val agentId = attributes.get(AttributeKey.stringKey("gen_ai.agent.id"))
                assertNotNull(agentId, "Agent span should have agent.id attribute")
                assertTrue(agentId.isNotEmpty(), "Agent span should have non-empty agent.id")
            }
            "run" -> {
                // Run spans should have gen_ai.operation.name = invoke_agent and gen_ai.agent.id
                val operationName = attributes.get(AttributeKey.stringKey("gen_ai.operation.name"))
                assertEquals(expectedAttributes["operation.name"], operationName, "Run span should have operation.name = ${expectedAttributes["operation.name"]}")

                val agentId = attributes.get(AttributeKey.stringKey("gen_ai.agent.id"))
                assertNotNull(agentId, "Run span should have agent.id attribute")
                assertTrue(agentId.isNotEmpty(), "Run span should have non-empty agent.id")
            }
            "node" -> {
                // Node spans don't have specific required attributes to check
                // Just verify they have some attributes
                assertTrue(attributes.size() > 0, "Node span should have attributes")
            }
            "llm" -> {
                // LLM spans should have gen_ai.operation.name = chat, gen_ai.system, and gen_ai.request.model
                val operationName = attributes.get(AttributeKey.stringKey("gen_ai.operation.name"))
                assertEquals(expectedAttributes["operation.name"], operationName, "LLM span should have operation.name = ${expectedAttributes["operation.name"]}")

                val system = attributes.get(AttributeKey.stringKey("gen_ai.system"))
                assertNotNull(system, "LLM span should have system attribute")

                val model = attributes.get(AttributeKey.stringKey("gen_ai.request.model"))
                assertNotNull(model, "LLM span should have request.model attribute")

                val temperature = attributes.get(AttributeKey.doubleKey("gen_ai.request.temperature"))
                assertNotNull(temperature, "LLM span should have request.temperature attribute")
            }
        }
    }

    private fun verifySpanHierarchy(expected: Map<String, String>, actual: List<SpanData>) {
        // Create a map of span ID to span
        val spanMap = actual.associateBy { it.spanContext.spanId }

        // Check parent-child relationships
        actual.forEach { span ->
            val parentSpanId = span.parentSpanId
            if (parentSpanId != SpanId.getInvalid()) {
                // If span has a parent, the parent should be in the collected spans
                val parentSpan = spanMap[parentSpanId]
                assertNotNull(parentSpan, "Parent span should be in collected spans")

                // Get the span type prefix (e.g., "run", "node", "llm")
                val spanTypePrefix = when {
                    span.name.startsWith("run.") -> "run"
                    span.name.startsWith("node.") -> "node"
                    span.name.startsWith("llm.") -> "llm"
                    else -> null
                }

                // If we have an expected parent type for this span type, verify it
                if (spanTypePrefix != null && expected.containsKey(spanTypePrefix)) {
                    val expectedParentPrefix = expected[spanTypePrefix]
                    assertTrue(
                        parentSpan.name.startsWith("$expectedParentPrefix."),
                        "$spanTypePrefix span should have $expectedParentPrefix span as parent"
                    )
                }
            }
        }
    }

    private fun verifySpanEvents(expected: Map<String, List<String>>, actual: List<SpanData>) {
        // Check that at least some spans have events
        val spansWithEvents = actual.filter { it.events.isNotEmpty() }
        assertTrue(spansWithEvents.isNotEmpty(), "At least some spans should have events")

        // Verify events in spans
        spansWithEvents.forEach { span ->
            // For all spans, verify that events have names and attributes
            span.events.forEach { event ->
                assertNotNull(event.name, "Event should have a name")
                assertTrue(event.name.isNotEmpty(), "Event name should not be empty")

                // Verify event attributes
                val attributes = event.attributes
                assertNotNull(attributes, "Event should have attributes")
                assertTrue(attributes.size() > 0, "Event should have at least one attribute")

                // Get event type suffix (e.g., "user.message", "choice")
                val eventTypeSuffix = expected.keys.find { event.name.endsWith(it) }

                // If we have expected attributes for this event type, verify them
                if (eventTypeSuffix != null) {
                    val expectedAttributes = expected[eventTypeSuffix] ?: emptyList()

                    // Verify each expected attribute exists
                    expectedAttributes.forEach { attrName ->
                        assertNotNull(
                            attributes.get(AttributeKey.stringKey(attrName)),
                            "$eventTypeSuffix event should have $attrName attribute"
                        )
                    }
                }
            }
        }
    }

    //endregion Private Methods
}
