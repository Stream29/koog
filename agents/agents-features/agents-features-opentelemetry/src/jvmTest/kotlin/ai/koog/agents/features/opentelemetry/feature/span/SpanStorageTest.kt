package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the SpanStorage class.
 */
class SpanStorageTest {

    @Test
    fun `test storage initial state`() {
        val spanStorage = SpanStorage()
        assertEquals(0, spanStorage.size)
    }

    @Test
    fun `test addSpan`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        val span = createMockSpan(spanId)

        spanStorage.addSpan(spanId, span)

        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test getSpan`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        val span = createMockSpan(spanId)

        spanStorage.addSpan(spanId, span)
        assertEquals(1, spanStorage.size)

        val actualSpan = spanStorage.getSpan<TraceSpanBase>(spanId)
        assertEquals(spanId, actualSpan?.spanId)
    }

    @Test
    fun `test getSpan returns null when no spans are added`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        assertEquals(0, spanStorage.size)

        val retrievedSpan = spanStorage.getSpan<TraceSpanBase>(spanId)

        assertNull(retrievedSpan)
        assertEquals(0,  spanStorage.size)
    }

    @Test
    fun `test getSpan returns null when span not found`() {
        val spanStorage = SpanStorage()

        val spanId = "test-span-id"
        val span = createMockSpan(spanId)
        spanStorage.addSpan(spanId, span)
        assertEquals(1, spanStorage.size)

        val nonExistentSpanId = "non-existent-span"
        val retrievedSpan = spanStorage.getSpan<TraceSpanBase>(nonExistentSpanId)

        assertNull(retrievedSpan)
        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test getSpanOrThrow returns span when found`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        val span = createMockSpan(spanId)
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(spanId, span)
        assertEquals(1, spanStorage.size)
        val retrievedSpan = spanStorage.getSpanOrThrow<TraceSpanBase>(spanId)

        assertEquals(spanId, retrievedSpan.spanId)
        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test getSpanOrThrow throws when span not found`() {
        val spanStorage = SpanStorage()
        val nonExistentSpanId = "non-existent-span"
        assertEquals(0, spanStorage.size)

        val exception = assertFailsWith<IllegalStateException> {
            spanStorage.getSpanOrThrow<TraceSpanBase>(nonExistentSpanId)
        }
        assertEquals("Span with id: $nonExistentSpanId not found", exception.message)
        assertEquals(0, spanStorage.size)
    }

    @Test
    fun `test getSpanOrThrow throws when span is of wrong type`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        val span = createMockSpan(spanId)
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(spanId, span)
        assertEquals(1, spanStorage.size)

        // We can't actually test this with our mock span since we can't create different types,
        // but we can verify the error message format by creating a fake exception
        val throwable = assertThrows<IllegalStateException> {
            spanStorage.getSpanOrThrow<AgentRunSpan>(spanId)
        }

        assertEquals(
            "Span with id <${spanId}> is not of expected type. Expected: <${AgentRunSpan::class.simpleName}>, actual: <${MockSpan::class.simpleName}>",
            throwable.message
        )

        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test getOrPutSpan returns existing span`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        val span = createMockSpan(spanId)
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(spanId, span)
        assertEquals(1, spanStorage.size)
        val retrievedSpan = spanStorage.getOrPutSpan(spanId) { createMockSpan("another-span-id") }

        assertEquals(spanId, retrievedSpan.spanId)
        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test getOrPutSpan creates new span when not found`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        assertEquals(0, spanStorage.size)

        val createdSpan = spanStorage.getOrPutSpan(spanId) { createMockSpan(spanId) }
        assertEquals(spanId, createdSpan.spanId)
        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test removeSpan removes and returns span`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        val span = createMockSpan(spanId)
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(spanId, span)
        assertEquals(1, spanStorage.size)

        val removedSpan = spanStorage.removeSpan<TraceSpanBase>(spanId)
        assertEquals(0, spanStorage.size)

        val retrievedSpan = spanStorage.getSpan<TraceSpanBase>(spanId)
        assertEquals(spanId, removedSpan?.spanId)
        assertNull(retrievedSpan)
        assertEquals(0, spanStorage.size)
    }

    @Test
    fun `test removeSpan returns null when span not found`() {
        val spanStorage = SpanStorage()
        val nonExistentSpanId = "non-existent-span"
        assertEquals(0, spanStorage.size)

        val removedSpan = spanStorage.removeSpan<TraceSpanBase>(nonExistentSpanId)
        assertNull(removedSpan)
        assertEquals(0, spanStorage.size)
    }

    @Test
    fun `test findTopMostSpan finds span with matching ID pattern`() {
        val spanStorage = SpanStorage()
        val agentId = "test-agent"
        val spanId = "agent.$agentId"
        val span = createMockSpan(spanId)
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(spanId, span)
        assertEquals(1, spanStorage.size)

        val foundSpan = spanStorage.findTopMostSpan(agentId)
        assertEquals(spanId, foundSpan?.spanId)
        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test findTopMostSpan finds more specific span when multiple matches exist`() {
        val spanStorage = SpanStorage()
        val agentId = "test-agent"
        val sessionId = "test-session"
        val agentSpanId = "agent.$agentId"
        val agentRunSpanId = "agent.$agentId.run.$sessionId"
        val agentSpan = createMockSpan(agentSpanId)
        val agentRunSpan = createMockSpan(agentRunSpanId)
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(agentSpanId, agentSpan)
        assertEquals(1, spanStorage.size)

        spanStorage.addSpan(agentRunSpanId, agentRunSpan)
        assertEquals(2, spanStorage.size)

        val foundSpan = spanStorage.findTopMostSpan(agentId, sessionId)
        assertEquals(agentRunSpanId, foundSpan?.spanId)
        assertEquals(2, spanStorage.size)
    }

    @Test
    fun `test endUnfinishedSpans with filter`() {
        val spanStorage = SpanStorage()
        val spanId1 = "test-span-1"
        val spanId2 = "test-span-2"
        val span1 = createMockSpan(spanId1)
        val span2 = createMockSpan(spanId2)
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(spanId1, span1)
        assertEquals(1, spanStorage.size)

        spanStorage.addSpan(spanId2, span2)
        assertEquals(2, spanStorage.size)
        
        // Verify that both spans are started but not ended
        assertTrue(span1.isStarted)
        assertFalse(span1.isEnded)
        assertTrue(span2.isStarted)
        assertFalse(span2.isEnded)
        
        // End spans that match the filter (only span1)
        spanStorage.endUnfinishedSpans { it.contains("1") }
        
        // Verify that span1 is ended but span2 is not
        assertTrue(span1.isEnded)
        assertFalse(span2.isEnded)
        
        // Verify that span1 has the correct status
        assertEquals(StatusCode.UNSET, span1.currentStatus)
        
        // Size should still be 2 since we're just ending the spans, not removing them
        assertEquals(2, spanStorage.size)
    }

    @Test
    fun `test endUnfinishedAgentRunSpans`() {
        val spanStorage = SpanStorage()
        val agentId = "test-agent"
        val sessionId = "test-session"
        val agentSpanId = "agent.$agentId"
        val agentRunSpanId = "agent.$agentId.run.$sessionId"
        val nodeSpanId = "agent.$agentId.run.$sessionId.node.test-node"
        val agentSpan = createMockSpan(agentSpanId)
        val agentRunSpan = createMockSpan(agentRunSpanId)
        val nodeSpan = createMockSpan(nodeSpanId)
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(agentSpanId, agentSpan)
        assertEquals(1, spanStorage.size)

        spanStorage.addSpan(agentRunSpanId, agentRunSpan)
        assertEquals(2, spanStorage.size)

        spanStorage.addSpan(nodeSpanId, nodeSpan)
        assertEquals(3, spanStorage.size)
        
        // Verify that all spans are started but not ended
        assertTrue(agentSpan.isStarted)
        assertFalse(agentSpan.isEnded)
        assertTrue(agentRunSpan.isStarted)
        assertFalse(agentRunSpan.isEnded)
        assertTrue(nodeSpan.isStarted)
        assertFalse(nodeSpan.isEnded)
        
        // End all spans except the agent span and agent run span
        spanStorage.endUnfinishedAgentRunSpans(agentId, sessionId)
        
        // Verify that only the node span is ended
        assertFalse(agentSpan.isEnded)
        assertFalse(agentRunSpan.isEnded)
        assertTrue(nodeSpan.isEnded)
        
        // Verify that the node span has the correct status
        assertEquals(StatusCode.UNSET, nodeSpan.currentStatus)
        
        // Size should still be 3 since we're just ending the spans, not removing them
        assertEquals(3, spanStorage.size)
    }

    /**
     * Helper method to create a mock span for testing
     */
    private fun createMockSpan(spanId: String): MockSpan {
        val mockTracer = MockTracer()
        val mockSpan = MockSpan(mockTracer, null, spanId)
        mockSpan.start() // Start the span so it can be ended
        return mockSpan
    }
}