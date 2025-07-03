package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.createAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.runBlocking
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

    @Test
    fun `test spans are created for agent with one llm call`() = runBlocking {
        MockSpanExporter().use { mockExporter ->

            val agentId = "test-agent-id"
            val promptId = "test-prompt-id"
            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4

            val strategy = strategy<String, String>("test-strategy") {
                val nodeSendInput by nodeLLMRequest("test-llm-call")

                edge(nodeStart forwardTo nodeSendInput)
                edge(nodeSendInput forwardTo nodeFinish transformed { it.content })
            }

            val agent = createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                model = model,
                temperature = temperature
            ) {
                install(OpenTelemetry) {
                    addSpanExporter(mockExporter)
                }
            }

            agent.run("Hello, how are you?")

            val collectedSpans = mockExporter.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            agent.close()

            // Check each span

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
                        "koog.agent.strategy" to "test-strategy",
                        "gen_ai.system" to "openai",
                        "gen_ai.agent.id" to "test-agent-id",
                        "gen_ai.conversation.id" to mockExporter.runId

                    ),
                    "events" to emptyMap()
                ),

                "node.test-llm-call" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to mockExporter.runId,
                        "koog.node.name" to "test-llm-call",
                    ),
                    "events" to emptyMap()
                ),

                "llm.test-prompt-id" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to "chat",
                        "gen_ai.system" to "openai",
                        "gen_ai.conversation.id" to mockExporter.runId,
                        "gen_ai.request.temperature" to 0.4,
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

    @Test
    fun `test spans are created for agent with tool call`() = runBlocking {
        MockSpanExporter().use { mockExporter ->

            val agentId = "test-agent-id"
            val promptId = "test-prompt-id"
            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4

            val strategy = strategy("test-strategy") {
                val nodeSendInput by nodeLLMRequest("test-llm-call")
                val toolCallNode by nodeExecuteTool("test-tool-call")

                edge(nodeStart forwardTo nodeSendInput)
                edge(nodeSendInput forwardTo toolCallNode onToolCall { true })
                edge(toolCallNode forwardTo nodeFinish transformed { it.content })
            }

            val agent = createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                model = model,
                temperature = temperature
            ) {
                install(OpenTelemetry) {
                    addSpanExporter(mockExporter)
                }
            }

            agent.run("Hello, how are you?")

            val collectedSpans = mockExporter.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            agent.close()

            // Check Spans

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
                        "koog.agent.strategy" to "test-strategy",
                        "gen_ai.system" to "openai",
                        "gen_ai.agent.id" to "test-agent-id",
                        "gen_ai.conversation.id" to mockExporter.runId

                    ),
                    "events" to emptyMap()
                ),

                "node.test-llm-call" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.conversation.id" to mockExporter.runId,
                        "koog.node.name" to "test-llm-call",
                    ),
                    "events" to emptyMap()
                ),

                "llm.test-prompt-id" to mapOf(
                    "attributes" to mapOf(
                        "gen_ai.operation.name" to "chat",
                        "gen_ai.system" to "openai",
                        "gen_ai.conversation.id" to mockExporter.runId,
                        "gen_ai.request.temperature" to 0.4,
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

    @Test
    fun `test spans are created for agent that run several times`() = runBlocking {
        MockSpanExporter().use { mockExporter ->

            val agentId = "test-agent-id"
            val promptId = "test-prompt-id"
            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4

            val strategy = strategy("test-strategy") {
                val nodeSendInput by nodeLLMRequest("test-llm-call")
                val toolCallNode by nodeExecuteTool("test-tool-call")

                edge(nodeStart forwardTo nodeSendInput)
                edge(nodeSendInput forwardTo toolCallNode onToolCall { true })
                edge(toolCallNode forwardTo nodeFinish transformed { it.content })
            }

            val agent = createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                model = model,
                temperature = temperature
            ) {
                install(OpenTelemetry) {
                    addSpanExporter(mockExporter)
                }
            }

            agent.run("Hello, how are you?")
            agent.run("What is the whether in Paris?")

            val collectedSpans = mockExporter.collectedSpans
            assertTrue(collectedSpans.isNotEmpty(), "Spans should be created during agent execution")

            agent.close()

            // Check Spans

            val expectedSpans = mapOf(
                "agent.test-agent-id" to emptyMap<String, Map<String, Any>>()
            )

            assertSpans(expectedSpans, collectedSpans)
        }
    }

    //region Private Methods

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

    //endregion Private Methods
}
