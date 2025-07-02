package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.MockTracer
import ai.koog.agents.features.opentelemetry.feature.MockGenAIAgentSpan
import ai.koog.agents.features.opentelemetry.feature.SpanProcessor
import io.opentelemetry.api.trace.StatusCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class SpanProcessorTest {

    @Test
    fun `test storage initial state`() {
        val spanProcessor = SpanProcessor(MockTracer())
        assertEquals(0, spanProcessor.spansCount)
    }

    @Test
    fun `test addSpan`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId)

        spanProcessor.startSpan(span)

        assertEquals(1, spanProcessor.spansCount)
    }

    @Test
    fun `test getSpan`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId)

        spanProcessor.startSpan(span)
        assertEquals(1, spanProcessor.spansCount)

        val actualSpan = spanProcessor.getSpan<GenAIAgentSpan>(spanId)
        assertEquals(spanId, actualSpan?.spanId)
    }

    @Test
    fun `test getSpan returns null when no spans are added`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val spanId = "test-span-id"
        assertEquals(0, spanProcessor.spansCount)

        val retrievedSpan = spanProcessor.getSpan<GenAIAgentSpan>(spanId)

        assertNull(retrievedSpan)
        assertEquals(0,  spanProcessor.spansCount)
    }

    @Test
    fun `test getSpan returns null when span not found`() {
        val spanProcessor = SpanProcessor(MockTracer())

        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId)
        spanProcessor.startSpan(span)
        assertEquals(1, spanProcessor.spansCount)

        val nonExistentSpanId = "non-existent-span"
        val retrievedSpan = spanProcessor.getSpan<GenAIAgentSpan>(nonExistentSpanId)

        assertNull(retrievedSpan)
        assertEquals(1, spanProcessor.spansCount)
    }

    @Test
    fun `test getSpanOrThrow returns span when found`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId)
        assertEquals(0, spanProcessor.spansCount)

        spanProcessor.startSpan(span)
        assertEquals(1, spanProcessor.spansCount)
        val retrievedSpan = spanProcessor.getSpanOrThrow<GenAIAgentSpan>(spanId)

        assertEquals(spanId, retrievedSpan.spanId)
        assertEquals(1, spanProcessor.spansCount)
    }

    @Test
    fun `test getSpanOrThrow throws when span not found`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val nonExistentSpanId = "non-existent-span"
        assertEquals(0, spanProcessor.spansCount)

        val exception = assertFailsWith<IllegalStateException> {
            spanProcessor.getSpanOrThrow<GenAIAgentSpan>(nonExistentSpanId)
        }
        assertEquals("Span with id: $nonExistentSpanId not found", exception.message)
        assertEquals(0, spanProcessor.spansCount)
    }

    @Test
    fun `test getSpanOrThrow throws when span is of wrong type`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId)
        assertEquals(0, spanProcessor.spansCount)

        spanProcessor.startSpan(span)
        assertEquals(1, spanProcessor.spansCount)

        // We can't test this with our mock span since we can't create different types,
        // but we can verify the error message format by creating a fake exception
        val throwable = assertThrows<IllegalStateException> {
            spanProcessor.getSpanOrThrow<InvokeAgentSpan>(spanId)
        }

        assertEquals(
            "Span with id <${spanId}> is not of expected type. Expected: <${InvokeAgentSpan::class.simpleName}>, actual: <${MockGenAIAgentSpan::class.simpleName}>",
            throwable.message
        )

        assertEquals(1, spanProcessor.spansCount)
    }

    @Test
    fun `test removeSpan removes and returns span`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val spanId = "test-span-id"
        val span = MockGenAIAgentSpan(spanId)
        assertEquals(0, spanProcessor.spansCount)

        spanProcessor.startSpan(span)
        assertEquals(1, spanProcessor.spansCount)

        spanProcessor.endSpan(spanId)
        assertEquals(0, spanProcessor.spansCount)

        val retrievedSpan = spanProcessor.getSpan<GenAIAgentSpan>(spanId)
        assertNull(retrievedSpan)
        assertEquals(0, spanProcessor.spansCount)
    }

    @Test
    fun `test removeSpan returns null when span not found`() {
        val spanProcessor = SpanProcessor(MockTracer())
        val nonExistentSpanId = "non-existent-span"
        assertEquals(0, spanProcessor.spansCount)

        val throwable = assertThrows<IllegalStateException> {
            spanProcessor.endSpan(nonExistentSpanId)
        }

        assertEquals(
            "Span with id '$nonExistentSpanId' not found. Make sure span was started or was not finished previously",
            throwable.message
        )
        assertEquals(0, spanProcessor.spansCount)
    }

    @Test
    fun `test endUnfinishedSpans ends spans that match the filter`() {
        val spanProcessor = SpanProcessor(MockTracer())
        
        // Create spans with different IDs
        val span1Id = "span1"
        val span2Id = "span2"
        val span3Id = "span3"
        
        // Create and start spans
        val span1 = MockGenAIAgentSpan(span1Id)
        spanProcessor.startSpan(span1)

        val span2 = MockGenAIAgentSpan(span2Id)
        spanProcessor.startSpan(span2)

        val span3 = MockGenAIAgentSpan(span3Id)
        spanProcessor.startSpan(span3)

        assertEquals(3, spanProcessor.spansCount)

        // End one of the spans
        spanProcessor.endSpan(span2.spanId)
        assertEquals(2, spanProcessor.spansCount)

        // Verify initial state
        assertTrue(span1.isStarted)
        assertFalse(span1.isEnded)
        assertTrue(span2.isStarted)
        assertTrue(span2.isEnded)
        assertTrue(span3.isStarted)
        assertFalse(span3.isEnded)
        
        // End spans that match the filter (only span1)
        spanProcessor.endUnfinishedSpans { id -> id == span1Id }
        
        // Verify span1 is ended, span2 was already ended, span3 is still not ended
        assertTrue(span1.isStarted)
        assertTrue(span1.isEnded)
        assertTrue(span2.isStarted)
        assertTrue(span2.isEnded)
        assertTrue(span3.isStarted)
        assertFalse(span3.isEnded)
        
        // End all remaining unfinished spans
        spanProcessor.endUnfinishedSpans()
        
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
        val spanProcessor = SpanProcessor(MockTracer())

        val agentId = "test-agent"
        val runId = "test-run"
        
        val agentSpanId = CreateAgentSpan.createId(agentId)
        val agentRunSpanId = InvokeAgentSpan.createId(agentId, runId)
        val nodeSpanId = "agent.$agentId.run.$runId.node.testNode"
        val toolSpanId = "agent.$agentId.run.$runId.node.testNode.tool.testTool"

        // Create and start spans
        val agentSpan = MockGenAIAgentSpan(agentSpanId)
        val agentRunSpan = MockGenAIAgentSpan(agentRunSpanId)
        val nodeSpan = MockGenAIAgentSpan(nodeSpanId)
        val toolSpan = MockGenAIAgentSpan(toolSpanId)

        // Add spans to storage
        spanProcessor.startSpan(agentSpan)
        spanProcessor.startSpan(agentRunSpan)
        spanProcessor.startSpan(nodeSpan)
        spanProcessor.startSpan(toolSpan)
        assertEquals(4, spanProcessor.spansCount)
        
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
        spanProcessor.endUnfinishedInvokeAgentSpans(agentId, runId)
        
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
