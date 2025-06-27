package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.trace.StatusCode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TraceSpanTest {

    @Test
    fun `test agent span creation and lifecycle`() {
        val mockTracer = MockTracer()
        val agentId = "test-agent"

        val agentSpan = AgentSpan(mockTracer, agentId)
        agentSpan.start()

        assertEquals(1, mockTracer.createdSpans.size, "One span should be created")
        assertTrue(mockTracer.createdSpans[0].isRecording(), "Span should be recording")
        assertFalse(mockTracer.createdSpans[0].isEnded, "Span should not be ended")

        agentSpan.end()
        assertTrue(mockTracer.createdSpans[0].isEnded, "Span should be ended")
    }

    @Test
    fun `test agent run span creation and lifecycle`() {
        // Arrange
        val mockTracer = MockTracer()
        val agentId = "test-agent"
        val sessionId = "test-session"
        val strategyName = "test-strategy"

        // Create parent span
        val agentSpan = AgentSpan(mockTracer, agentId)
        agentSpan.start()

        // Act
        val agentRunSpan = AgentRunSpan(
            tracer = mockTracer,
            parentSpan = agentSpan,
            sessionId = sessionId,
            strategyName = strategyName
        )
        agentRunSpan.start()

        // Assert
        assertEquals(2, mockTracer.createdSpans.size, "Two spans should be created")
        assertTrue(mockTracer.createdSpans[1].isRecording(), "Agent run span should be recording")
        assertFalse(mockTracer.createdSpans[1].isEnded, "Agent run span should not be ended")

        // Act - end the span
        agentRunSpan.end(completed = true, result = "test-result", statusCode = StatusCode.OK)

        // Assert
        assertTrue(mockTracer.createdSpans[1].isEnded, "Agent run span should be ended")
        assertEquals(StatusCode.OK, (mockTracer.createdSpans[1] as MockSpan).status, "Agent run span should have OK status")
    }

    @Test
    fun `test node execute span creation and lifecycle`() {
        // Arrange
        val mockTracer = MockTracer()
        val agentId = "test-agent"
        val sessionId = "test-session"
        val strategyName = "test-strategy"
        val nodeName = "test-node"

        // Create parent spans
        val agentSpan = AgentSpan(mockTracer, agentId)
        agentSpan.start()

        val agentRunSpan = AgentRunSpan(
            tracer = mockTracer,
            parentSpan = agentSpan,
            sessionId = sessionId,
            strategyName = strategyName
        )
        agentRunSpan.start()

        // Act
        val nodeExecuteSpan = NodeExecuteSpan(
            tracer = mockTracer,
            parentSpan = agentRunSpan,
            nodeName = nodeName
        )
        nodeExecuteSpan.start()

        // Assert
        assertEquals(3, mockTracer.createdSpans.size, "Three spans should be created")
        assertTrue(mockTracer.createdSpans[2].isRecording(), "Node execute span should be recording")
        assertFalse(mockTracer.createdSpans[2].isEnded, "Node execute span should not be ended")

        // Act - end the span
        nodeExecuteSpan.end()

        // Assert
        assertTrue(mockTracer.createdSpans[2].isEnded, "Node execute span should be ended")
        assertEquals(StatusCode.OK, (mockTracer.createdSpans[2] as MockSpan).status, "Node execute span should have OK status")
    }

    @Test
    fun `test span hierarchy and ID structure`() {
        // Arrange
        val mockTracer = MockTracer()
        val agentId = "test-agent"
        val sessionId = "test-session"
        val strategyName = "test-strategy"
        val nodeName = "test-node"

        // Create spans
        val agentSpan = AgentSpan(mockTracer, agentId)
        val agentRunSpan = AgentRunSpan(
            tracer = mockTracer,
            parentSpan = agentSpan,
            sessionId = sessionId,
            strategyName = strategyName
        )
        val nodeExecuteSpan = NodeExecuteSpan(
            tracer = mockTracer,
            parentSpan = agentRunSpan,
            nodeName = nodeName
        )

        // Assert
        assertEquals("agent.$agentId", agentSpan.spanId, "Agent span ID should be correctly formatted")
        assertEquals("agent.$agentId.run.$sessionId", agentRunSpan.spanId, "Agent run span ID should be correctly formatted")
        assertEquals("agent.$agentId.run.$sessionId.node.$nodeName", nodeExecuteSpan.spanId, "Node execute span ID should be correctly formatted")

        // Verify parent-child relationships
        assertNull(agentSpan.parentSpan, "Agent span should have no parent")
        assertEquals(agentSpan, agentRunSpan.parentSpan, "Agent run span's parent should be agent span")
        assertEquals(agentRunSpan, nodeExecuteSpan.parentSpan, "Node execute span's parent should be agent run span")
    }

    @Test
    fun `test span storage with multiple spans`() {
        // Arrange
        val mockTracer = MockTracer()
        val spanStorage = SpanStorage()
        val agentId = "test-agent"
        val sessionId = "test-session"
        val strategyName = "test-strategy"
        val nodeName = "test-node"

        // Create spans
        val agentSpan = AgentSpan(mockTracer, agentId)
        agentSpan.start()

        val agentRunSpan = AgentRunSpan(
            tracer = mockTracer,
            parentSpan = agentSpan,
            sessionId = sessionId,
            strategyName = strategyName
        )
        agentRunSpan.start()

        val nodeExecuteSpan = NodeExecuteSpan(
            tracer = mockTracer,
            parentSpan = agentRunSpan,
            nodeName = nodeName
        )
        nodeExecuteSpan.start()

        // Act - add spans to storage
        spanStorage.addSpan(agentSpan.spanId, agentSpan)
        spanStorage.addSpan(agentRunSpan.spanId, agentRunSpan)
        spanStorage.addSpan(nodeExecuteSpan.spanId, nodeExecuteSpan)

        // Assert
        assertEquals(3, spanStorage.size, "Storage should contain three spans")

        // Verify retrieval
        val retrievedAgentSpan = spanStorage.getSpan<AgentSpan>(agentSpan.spanId)
        val retrievedAgentRunSpan = spanStorage.getSpan<AgentRunSpan>(agentRunSpan.spanId)
        val retrievedNodeExecuteSpan = spanStorage.getSpan<NodeExecuteSpan>(nodeExecuteSpan.spanId)

        assertNotNull(retrievedAgentSpan, "Agent span should be retrievable")
        assertNotNull(retrievedAgentRunSpan, "Agent run span should be retrievable")
        assertNotNull(retrievedNodeExecuteSpan, "Node execute span should be retrievable")

        assertEquals(agentId, retrievedAgentSpan?.agentId, "Retrieved agent span should have correct agent ID")
        assertEquals(sessionId, retrievedAgentRunSpan?.sessionId, "Retrieved agent run span should have correct session ID")
        assertEquals(nodeName, retrievedNodeExecuteSpan?.nodeName, "Retrieved node execute span should have correct node name")
    }

}