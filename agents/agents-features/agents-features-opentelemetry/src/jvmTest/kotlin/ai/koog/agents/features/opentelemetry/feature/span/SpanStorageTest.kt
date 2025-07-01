package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.features.opentelemetry.feature.MockGenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.CreateAgentSpan
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.feature.SpanStorage
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
        val span = MockGenAIAgentSpan(spanId).also { it.start() }

        spanStorage.addSpan(spanId, span)

        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test getSpan`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId).also { it.start() }

        spanStorage.addSpan(spanId, span)
        assertEquals(1, spanStorage.size)

        val actualSpan = spanStorage.getSpan<GenAIAgentSpan>(spanId)
        assertEquals(spanId, actualSpan?.spanId)
    }

    @Test
    fun `test getSpan returns null when no spans are added`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        assertEquals(0, spanStorage.size)

        val retrievedSpan = spanStorage.getSpan<GenAIAgentSpan>(spanId)

        assertNull(retrievedSpan)
        assertEquals(0,  spanStorage.size)
    }

    @Test
    fun `test getSpan returns null when span not found`() {
        val spanStorage = SpanStorage()

        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId).also { it.start() }
        spanStorage.addSpan(spanId, span)
        assertEquals(1, spanStorage.size)

        val nonExistentSpanId = "non-existent-span"
        val retrievedSpan = spanStorage.getSpan<GenAIAgentSpan>(nonExistentSpanId)

        assertNull(retrievedSpan)
        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test getSpanOrThrow returns span when found`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId).also { it.start() }
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(spanId, span)
        assertEquals(1, spanStorage.size)
        val retrievedSpan = spanStorage.getSpanOrThrow<GenAIAgentSpan>(spanId)

        assertEquals(spanId, retrievedSpan.spanId)
        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test getSpanOrThrow throws when span not found`() {
        val spanStorage = SpanStorage()
        val nonExistentSpanId = "non-existent-span"
        assertEquals(0, spanStorage.size)

        val exception = assertFailsWith<IllegalStateException> {
            spanStorage.getSpanOrThrow<GenAIAgentSpan>(nonExistentSpanId)
        }
        assertEquals("Span with id: $nonExistentSpanId not found", exception.message)
        assertEquals(0, spanStorage.size)
    }

    @Test
    fun `test getSpanOrThrow throws when span is of wrong type`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId).also { it.start() }
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(spanId, span)
        assertEquals(1, spanStorage.size)

        // We can't test this with our mock span since we can't create different types,
        // but we can verify the error message format by creating a fake exception
        val throwable = assertThrows<IllegalStateException> {
            spanStorage.getSpanOrThrow<AgentRunSpan>(spanId)
        }

        assertEquals(
            "Span with id <${spanId}> is not of expected type. Expected: <${AgentRunSpan::class.simpleName}>, actual: <${MockGenAIAgentSpan::class.simpleName}>",
            throwable.message
        )

        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test getOrPutSpan returns existing span`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId).also { it.start() }
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(spanId, span)
        assertEquals(1, spanStorage.size)
        val retrievedSpan = spanStorage.getOrPutSpan(spanId) { MockGenAIAgentSpan("another-span-id").also { it.start() } }

        assertEquals(spanId, retrievedSpan.spanId)
        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test getOrPutSpan creates new span when not found`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        assertEquals(0, spanStorage.size)

        val createdSpan = spanStorage.getOrPutSpan(spanId) { MockGenAIAgentSpan(spanId).also { it.start() } }
        assertEquals(spanId, createdSpan.spanId)
        assertEquals(1, spanStorage.size)
    }

    @Test
    fun `test removeSpan removes and returns span`() {
        val spanStorage = SpanStorage()
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId).also { it.start() }
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(spanId, span)
        assertEquals(1, spanStorage.size)

        val removedSpan = spanStorage.removeSpan<GenAIAgentSpan>(spanId)
        assertEquals(0, spanStorage.size)

        val retrievedSpan = spanStorage.getSpan<GenAIAgentSpan>(spanId)
        assertEquals(spanId, removedSpan?.spanId)
        assertNull(retrievedSpan)
        assertEquals(0, spanStorage.size)
    }

    @Test
    fun `test removeSpan returns null when span not found`() {
        val spanStorage = SpanStorage()
        val nonExistentSpanId = "non-existent-span"
        assertEquals(0, spanStorage.size)

        val removedSpan = spanStorage.removeSpan<GenAIAgentSpan>(nonExistentSpanId)
        assertNull(removedSpan)
        assertEquals(0, spanStorage.size)
    }

    @Test
    fun `test findTopMostSpan finds span with matching ID pattern`() {
        val spanStorage = SpanStorage()
        val agentId = "test-agent"
        val spanId = "agent.$agentId"
        val span = MockGenAIAgentSpan(spanId).also { it.start() }
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
        val runId = "test-run"
        val agentSpanId = "agent.$agentId"
        val agentRunSpanId = "agent.$agentId.run.$runId"
        val agentSpan = MockGenAIAgentSpan(agentSpanId).also { it.start() }
        val agentRunSpan = MockGenAIAgentSpan(agentRunSpanId).also { it.start() }
        assertEquals(0, spanStorage.size)

        spanStorage.addSpan(agentSpanId, agentSpan)
        assertEquals(1, spanStorage.size)

        spanStorage.addSpan(agentRunSpanId, agentRunSpan)
        assertEquals(2, spanStorage.size)

        val foundSpan = spanStorage.findTopMostSpan(agentId, runId)
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
        val span1 = MockGenAIAgentSpan(span1Id).also { it.start() }
        val span2 = MockGenAIAgentSpan(span2Id).also { it.start() }
        val span3 = MockGenAIAgentSpan(span3Id).also { it.start() }
        
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

        val agentId = "test-agent"
        val runId = "test-run"
        
        val agentSpanId = CreateAgentSpan.createId(agentId)
        val agentRunSpanId = AgentRunSpan.createId(agentId, runId)
        val nodeSpanId = "agent.$agentId.run.$runId.node.testNode"
        val toolSpanId = "agent.$agentId.run.$runId.node.testNode.tool.testTool"

        // Create and start spans
        val agentSpan = MockGenAIAgentSpan(agentSpanId).also { it.start() }
        val agentRunSpan = MockGenAIAgentSpan(agentRunSpanId).also { it.start() }
        val nodeSpan = MockGenAIAgentSpan(nodeSpanId).also { it.start() }
        val toolSpan = MockGenAIAgentSpan(toolSpanId).also { it.start() }

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
        spanStorage.endUnfinishedAgentRunSpans(agentId, runId)
        
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
