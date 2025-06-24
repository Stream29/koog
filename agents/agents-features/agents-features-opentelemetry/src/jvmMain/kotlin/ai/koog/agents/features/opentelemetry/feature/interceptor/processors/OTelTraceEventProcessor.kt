package ai.koog.agents.features.opentelemetry.feature.interceptor.processors

import ai.koog.agents.core.feature.model.*
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessor
import ai.koog.agents.features.opentelemetry.feature.interceptor.context.OTelContextManager
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer


/**
 * Not used
 */
public class OTelTraceEventProcessor(tracer: Tracer) : FeatureMessageProcessor() {

    private val contextManager = OTelContextManager(tracer)

    /**
     * Hierarchy
     * AIAgentStartedEvent
     *  AIAgentNodeExecutionStartEvent
     *   LLMCallStartEvent
     *   LLMCallEndEvent
     *  AIAgentNodeExecutionEndEvent
     * AIAgentFinishedEvent
     * AIAgentRunErrorEvent
     */
    override suspend fun processMessage(message: FeatureMessage) {
        val (span, scope) = contextManager.createSpanForMessage(message)

        try {
            // Process the event with span context
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
        } finally {
            scope.close()
            span.end()
        }
    }

    override suspend fun close() {
        contextManager.close()
    }

}