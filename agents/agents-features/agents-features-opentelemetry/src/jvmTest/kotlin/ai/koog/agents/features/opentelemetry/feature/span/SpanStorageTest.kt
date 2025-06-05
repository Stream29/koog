package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.features.opentelemetry.feature.MockTraceSpan
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

        // We can't test this with our mock span since we can't create different types,
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
    fun `test endUnfinishedSpans ends spans that match the filter`() {
        val spanStorage = SpanStorage()
        
        // Create spans with different IDs
        val span1Id = "span1"
        val span2Id = "span2"
        val span3Id = "span3"
        
        // Create and start spans
        val span1 = MockTraceSpan(span1Id).also { it.start() }
        val span2 = MockTraceSpan(span2Id).also { it.start() }
        val span3 = MockTraceSpan(span3Id).also { it.start() }
        
        // End one of the spans
        span2.end()
        
        // Add spans to storage
        spanStorage.addSpan(span1Id, span1)
        spanStorage.addSpan(span2Id, span2)
        spanStorage.addSpan(span3Id, span3)
        assertEquals(3, spanStorage.size)
        
        // Verify initial state
        assertTrue(span1.isStarted)
        assertFalse(span1.isEnded)
        assertTrue(span2.isStarted)
        assertTrue(span2.isEnded)
        assertTrue(span3.isStarted)
        assertFalse(span3.isEnded)
        
        // End spans that match the filter (only span1)
        spanStorage.endUnfinishedSpans { id -> id == span1Id }
        
        // Verify span1 is ended, span2 was already ended, span3 is still not ended
        assertTrue(span1.isStarted)
        assertTrue(span1.isEnded)
        assertTrue(span2.isStarted)
        assertTrue(span2.isEnded)
        assertTrue(span3.isStarted)
        assertFalse(span3.isEnded)
        
        // End all remaining unfinished spans
        spanStorage.endUnfinishedSpans()
        
        // Verify all spans are ended
        assertTrue(span1.isStarted)
        assertTrue(span1.isEnded)
        assertTrue(span2.isStarted)
        assertTrue(span2.isEnded)
        assertTrue(span3.isStarted)
        assertTrue(span3.isEnded)
        
        // Verify status code is set to UNSET for spans ended by endUnfinishedSpans
        assertEquals(StatusCode.UNSET, span1.currentStatus)
        assertEquals(StatusCode.UNSET, span3.currentStatus)
    }

    @Test
    fun `test endUnfinishedAgentRunSpans ends all spans except agent and agent run spans`() {
        val spanStorage = SpanStorage()
        
        // Create agent and session IDs
        val agentId = "test-agent"
        val sessionId = "test-session"
        
        // Create span IDs
        val agentSpanId = AgentSpan.createId(agentId)
        val agentRunSpanId = AgentRunSpan.createId(agentId, sessionId)
        val nodeSpanId = "agent.$agentId.run.$sessionId.node.testNode"
        val toolSpanId = "agent.$agentId.run.$sessionId.node.testNode.tool.testTool"
        
        // Create and start spans
        val agentSpan = MockTraceSpan(agentSpanId).also { it.start() }
        val agentRunSpan = MockTraceSpan(agentRunSpanId).also { it.start() }
        val nodeSpan = MockTraceSpan(nodeSpanId).also { it.start() }
        val toolSpan = MockTraceSpan(toolSpanId).also { it.start() }
        
        // Add spans to storage
        spanStorage.addSpan(agentSpanId, agentSpan)
        spanStorage.addSpan(agentRunSpanId, agentRunSpan)
        spanStorage.addSpan(nodeSpanId, nodeSpan)
        spanStorage.addSpan(toolSpanId, toolSpan)
        assertEquals(4, spanStorage.size)
        
        // Verify initial state - all spans are started but not ended
        assertTrue(agentSpan.isStarted)
        assertFalse(agentSpan.isEnded)
        assertTrue(agentRunSpan.isStarted)
        assertFalse(agentRunSpan.isEnded)
        assertTrue(nodeSpan.isStarted)
        assertFalse(nodeSpan.isEnded)
        assertTrue(toolSpan.isStarted)
        assertFalse(toolSpan.isEnded)
        
        // End unfinished agent run spans
        spanStorage.endUnfinishedAgentRunSpans(agentId, sessionId)
        
        // Verify that node and tool spans are ended, but agent and agent run spans are not
        assertTrue(agentSpan.isStarted)
        assertFalse(agentSpan.isEnded)
        assertTrue(agentRunSpan.isStarted)
        assertFalse(agentRunSpan.isEnded)
        assertTrue(nodeSpan.isStarted)
        assertTrue(nodeSpan.isEnded)
        assertTrue(toolSpan.isStarted)
        assertTrue(toolSpan.isEnded)
        
        // Verify status code is set to UNSET for spans ended by endUnfinishedAgentRunSpans
        assertEquals(StatusCode.UNSET, nodeSpan.currentStatus)
        assertEquals(StatusCode.UNSET, toolSpan.currentStatus)
    }
}
