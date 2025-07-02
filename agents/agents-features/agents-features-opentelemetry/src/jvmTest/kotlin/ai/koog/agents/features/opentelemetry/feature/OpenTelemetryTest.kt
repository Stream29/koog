package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import io.github.oshai.kotlinlogging.KotlinLogging
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

    companion object {
        private val logger = KotlinLogging.logger { }
    }

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

            // Check Span names
            val expectedSpanNames = listOf(
                "agent.test-agent-id",
                "run.${mockExporter.runId}",
                "node.nodeCallLLM",
                "llm.chat",
                "node.__start__",
            )

            val actualSpanNames = collectedSpans.map { it.name }

            assertEquals(expectedSpanNames.size, actualSpanNames.size)
            assertContentEquals(expectedSpanNames, actualSpanNames)

            // Check each span Attributes

            val expectedSpans = mapOf(
                "agent.test-agent-id" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to "create_agent",
                        "gen_ai.system" to "openai",
                        "gen_ai.agent.id" to "test-agent-id",
                        "gen_ai.request.model" to "gpt-4o"
                    ),
                    "events" to emptyMap()
                ),

                "run.${mockExporter.runId}" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to "invoke_agent",
                        "koog.agent.strategy" to "single_run",
                        "gen_ai.system" to "openai",
                        "gen_ai.agent.id" to "test-agent-id",
                        "gen_ai.conversation.id" to mockExporter.runId

                    ),
                    "events" to emptyMap()
                ),

                "node.nodeCallLLM" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to mockExporter.runId,
                        "koog.node.name" to "nodeCallLLM"
                    ),
                    "events" to emptyMap()
                ),

                "llm.chat" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to "chat",
                        "gen_ai.system" to "openai",
                        "gen_ai.conversation.id" to mockExporter.runId,
                        "gen_ai.request.temperature" to 1.0,
                        "gen_ai.request.model" to "gpt-4o",
                    ),
                    "events" to mapOf(
                        "gen_ai.user.message" to mapOf(
                            "gen_ai.system" to "openai",
                            "content" to "Hello, how are you?"
                        )
                    )
                ),

                "node.__start__" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to mockExporter.runId,
                        "koog.node.name" to "__start__"
                    ),
                    "events" to emptyMap()
                )
            )

            assertSpans(expectedSpans, collectedSpans)
        }
    }

    /**
     * Expected Span:
     *   Map<SpanName, Map<Any>>
     *       where Any = "attributes" or "events"
     *       attributes: Map<AttributeKey, AttributeValue>
     *       events: Map<EventName, Attributes>
     *           Attributes: Map<AttributeKey, AttributeValue>
     */
    private fun assertSpans(expectedSpans: Map<String, Map<String, Any>>, actualSpans: List<SpanData>) {
        // Span names
        val expectedSpanNames = expectedSpans.keys.toList()
        val actualSpanNames = actualSpans.map { it.name }

        assertSpanNames(expectedSpanNames, actualSpanNames)

        // Span attributes + events
        actualSpans.forEach { actualSpan ->

            val expectedSpanData = expectedSpans[actualSpan.name]
            assertNotNull(expectedSpanData, "Span (name: ${actualSpan.name}) not found in expected spans")

            val spanName = actualSpan.name

            // Attributes
            val expectedAttributes = expectedSpanData["attributes"] as Map<String, Any>
            val actualAttributes = actualSpan.attributes.asMap().asSequence().associate {
                it.key.key to it.value
            }

            assertAttributes(spanName, expectedAttributes, actualAttributes)

            // Events
            val expectedEvents = expectedSpanData["events"] as Map<String, Map<String, Any>>
            val actualEvents = actualSpan.events.associate { event ->
                val actualEventAttributes = event.attributes.asMap().asSequence().associate { (key, value) ->
                    key.key to value
                }
                event.name to actualEventAttributes
            }

            assertEventsForSpan(spanName, expectedEvents, actualEvents)

        }
    }

    private fun assertSpanNames(expectedSpanNames: List<String>, actualSpanNames: List<String>) {
        assertEquals(expectedSpanNames.size, actualSpanNames.size, "Expected collection of spans should be the same size")
        assertContentEquals(expectedSpanNames, actualSpanNames, "Expected collection of spans should be the same as actual")
    }

    /**
     * Event:
     *   Map<EventName, Attributes> -> Map<EventName, Map<AttributeKey, AttributeValue>>
     */
    private fun assertEventsForSpan(
        spanName: String,
        expectedEvents: Map<String, Map<String, Any>>,
        actualEvents: Map<String, Map<String, Any>>
    ) {
        logger.debug { "Asserting events for the Span (name: $spanName).\nExpected events:\n$expectedEvents\nActual events:\n$actualEvents" }

        assertEquals(
            expectedEvents.size,
            actualEvents.size,
            "Expected collection of events should be the same size for the span (name: $spanName)"
        )

        actualEvents.forEach { (actualEventName, actualEventAttributes) ->

            logger.debug { "Asserting event (name: $actualEventName) for the Span (name: $spanName)" }

            val expectedEventAttributes = expectedEvents[actualEventName]
            assertNotNull(expectedEventAttributes, "Event (name: $actualEventName) not found in expected events for span (name: $spanName)")

            assertAttributes(spanName, expectedEventAttributes, actualEventAttributes)
        }
    }

    /**
     * Attribute:
     *   Map<AttributeKey, AttributeValue>
     */
    private fun assertAttributes(
        spanName: String,
        expectedAttributes: Map<String, Any>,
        actualAttributes: Map<String, Any>
    ) {
        logger.debug { "Asserting attributes for the Span (name: $spanName).\nExpected attributes:\n$expectedAttributes\nActual attributes:\n$actualAttributes" }

        assertEquals(
            expectedAttributes.size,
            actualAttributes.size,
            "Expected collection of attributes should be the same size for the span (name: $spanName)"
        )

        actualAttributes.forEach { (actualArgName, actualArgValue) ->

            logger.debug { "Find expected attribute (name: $actualArgName) for the Span (name: $spanName)" }
            val expectedArgValue = expectedAttributes[actualArgName]

            assertNotNull(expectedArgValue, "Attribute (name: $actualArgName) not found in expected attributes for span (name: $spanName)")
            assertEquals(expectedArgValue, actualArgValue, "Attribute values should be the same for the span (name: $spanName)()")
        }
    }

    // TODO: SD -- fix this
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
//                verifySpanAttributes(expectedAttributes, span)
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
//            verifySpanEvents(expectedEvents, mockExporter.collectedSpans)

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

    //endregion Private Methods
}
