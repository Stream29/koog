package ai.koog.agents.features.opentelemetry.feature.interceptor.processors

import ai.koog.agents.core.feature.model.*
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessor
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context


/**
 *
 */
public class NewOTelTraceEventProcessor(private val tracer: Tracer) : FeatureMessageProcessor() {
    /**
     * Hierarchy
     * AIAgentStartedEvent
     *  AIAgentNodeExecutionStartEvent
     *   LLMCallStartEvent
     *   LLMCallEndEvent
     *  AIAgentNodeExecutionEndEvent
     * AIAgentFinishedEvent
     */
    private val spanStack = ArrayDeque<Span>() // Для хранения активных спанов

    //TODO: tool call event, test scenario with multiple tools
    override suspend fun processMessage(message: FeatureMessage) {
        when (message) {
            is AIAgentStartedEvent -> {
                val span = tracer
                    .spanBuilder(message.strategyName)
                    .startSpan()
                addAttributes(span, message)
                spanStack.addLast(span)
            }

            is AIAgentFinishedEvent -> {
                val span = spanStack.removeLastOrNull()
                addAttributes(span ?: return, message)
                span?.end()
            }

            is AIAgentNodeExecutionStartEvent -> {
                val parentSpan = spanStack.lastOrNull()
                val span = tracer
                    .spanBuilder(message.nodeName)
                    .setParent(parentSpan?.storeInContext(Context.current()) ?: Context.current())
                    .startSpan()
                addAttributes(span, message)
                spanStack.addLast(span)
            }
            is AIAgentNodeExecutionEndEvent -> {
                val span = spanStack.removeLastOrNull()
                addAttributes(span ?: return, message)
                span.end()
            }
            is LLMCallStartEvent -> {
                val parentSpan = spanStack.lastOrNull()
                val span = tracer
                    .spanBuilder("LLMCall")
                    .setParent(parentSpan?.storeInContext(Context.current()) ?: Context.current())
                    .startSpan()
                addAttributes(span, message)
                spanStack.addLast(span)
            }
            is LLMCallEndEvent -> {
                val span = spanStack.removeLastOrNull()
                addAttributes(span ?: return, message)
                span.end()
            }
        }
    }


    // TODO: decide what to add to the attributes, follow semantic conventions
    private fun addAttributes(span: Span, message: FeatureMessage) {
        when (message) {
            is AIAgentStartedEvent -> {
                span.setAttribute("koog.event.strategy.name", message.strategyName)
                span.setAttribute("koog.event.eventId", message.eventId)
            }

            is AIAgentFinishedEvent -> {
                span.setAttribute("koog.event.result", message.result)
            }

            is AIAgentRunErrorEvent -> {
                span.setAttribute("koog.event.error.message", message.error.message)
                span.setAttribute("koog.event.error.full", message.error.toString())
                span.setStatus(StatusCode.ERROR, message.error.message)
            }

            is AIAgentStrategyStartEvent -> {
                span.setAttribute("koog.event.strategy.start", true)
            }

            is AIAgentStrategyFinishedEvent -> {
                span.setAttribute("koog.event.strategy.finished.result", message.result)
            }

            is AIAgentNodeExecutionStartEvent -> {
                span.setAttribute("koog.event.node.execution.start", true)
            }

            is AIAgentNodeExecutionEndEvent -> {
                span.setAttribute("koog.event.node.execution.end", true)
            }

            is LLMCallStartEvent -> {
                span.setAttribute("koog.llm.call.prompt", message.prompt.toString())
            }

            is LLMCallEndEvent -> {
                span.setAttribute("koog.llm.call.responses", message.responses.toString())
            }

            is ToolCallEvent -> {
                span.setAttribute("koog.tool.name", message.toolName)
                span.setAttribute("koog.tool.args", message.toolArgs.toString())
                span.setAttribute("koog.tool.call", true)
            }

            is ToolValidationErrorEvent -> {
                span.setStatus(StatusCode.ERROR, "Tool validation error")
            }

            is ToolCallFailureEvent -> {
                span.setStatus(StatusCode.ERROR, "Tool call failure")
            }

            is ToolCallResultEvent -> {
                span.setAttribute("koog.tool.call.result", true)
            }
        }
    }

    override suspend fun close() {
        spanStack.forEach { it.end() }
    }
}