package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.trace.StatusCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

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
        val span = MockTraceSpan(spanId).also { it.start() }

        spanStorage.addSpan(spanId, span)

        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test getSpan`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        val span = MockTraceSpan(spanId).also { it.start() }

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
        val span = MockTraceSpan(spanId).also { it.start() }
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
        val span = MockTraceSpan(spanId).also { it.start() }
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
        val span = MockTraceSpan(spanId).also { it.start() }
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(spanId, span)
        assertEquals(1, spanStorage.size)

        // We can't actually test this with our mock span since we can't create different types,
        // but we can verify the error message format by creating a fake exception
        val throwable = assertThrows<IllegalStateException> {
            spanStorage.getSpanOrThrow<AgentRunSpan>(spanId)
        }

        assertEquals(
            "Span with id <${spanId}> is not of expected type. Expected: <${AgentRunSpan::class.simpleName}>, actual: <${MockTraceSpan::class.simpleName}>",
            throwable.message
        )

        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test getOrPutSpan returns existing span`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        val span = MockTraceSpan(spanId).also { it.start() }
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(spanId, span)
        assertEquals(1, spanStorage.size)
        val retrievedSpan = spanStorage.getOrPutSpan(spanId) { MockTraceSpan("another-span-id").also { it.start() } }

        assertEquals(spanId, retrievedSpan.spanId)
        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test getOrPutSpan creates new span when not found`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        assertEquals(0, spanStorage.size)

        val createdSpan = spanStorage.getOrPutSpan(spanId) { MockTraceSpan(spanId).also { it.start() } }
        assertEquals(spanId, createdSpan.spanId)
        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test removeSpan removes and returns span`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        val span = MockTraceSpan(spanId).also { it.start() }
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
        val span = MockTraceSpan(spanId).also { it.start() }
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
        val agentSpan = MockTraceSpan(agentSpanId).also { it.start() }
        val agentRunSpan = MockTraceSpan(agentRunSpanId).also { it.start() }
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
        val span1 = MockTraceSpan(spanId1).also { it.start() }
        val span2 = MockTraceSpan(spanId2).also { it.start() }
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(spanId1, span1)
        assertEquals(1, spanStorage.size)

        spanStorage.addSpan(spanId2, span2)
        assertEquals(2, spanStorage.size)
        
        // Both spans are started but not ended
        assertTrue(span1.isStarted)
        assertFalse(span1.isEnded)
        assertTrue(span2.isStarted)
        assertFalse(span2.isEnded)
        
        // End spans that match the filter (only span1)
        spanStorage.endUnfinishedSpans { it.contains("1") }
        
        assertTrue(span1.isEnded)
        assertFalse(span2.isEnded)
        assertEquals(StatusCode.UNSET, span1.currentStatus)

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

        val agentSpan = MockTraceSpan(agentSpanId).also { it.start() }
        val agentRunSpan = MockTraceSpan(agentRunSpanId).also { it.start() }
        val nodeSpan = MockTraceSpan(nodeSpanId).also { it.start() }
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(agentSpanId, agentSpan)
        assertEquals(1, spanStorage.size)

        spanStorage.addSpan(agentRunSpanId, agentRunSpan)
        assertEquals(2, spanStorage.size)

        spanStorage.addSpan(nodeSpanId, nodeSpan)
        assertEquals(3, spanStorage.size)
        
        // All spans are started but not ended
        assertTrue(agentSpan.isStarted)
        assertFalse(agentSpan.isEnded)
        assertTrue(agentRunSpan.isStarted)
        assertFalse(agentRunSpan.isEnded)
        assertTrue(nodeSpan.isStarted)
        assertFalse(nodeSpan.isEnded)
        
        // End all spans except the agent span and agent run span
        spanStorage.endUnfinishedAgentRunSpans(agentId, sessionId)

        assertFalse(agentSpan.isEnded)
        assertFalse(agentRunSpan.isEnded)
        assertTrue(nodeSpan.isEnded)

        assertEquals(StatusCode.UNSET, nodeSpan.currentStatus)
        
        assertEquals(3, spanStorage.size)
    }
}
