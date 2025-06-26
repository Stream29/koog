package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.context.element.getAgentRunInfoElement
import ai.koog.agents.core.agent.context.element.getNodeInfoElement
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.features.opentelemetry.feature.span.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.currentCoroutineContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents the OpenTelemetry integration feature for tracking and managing spans and contexts
 * within the AI Agent framework. This class manages the lifecycle of spans for various operations,
 * including agent executions, node processing, LLM calls, and tool calls.
 */
public class OpenTelemetry {

    /**
     * Companion object implementing the AIAgentFeature interface to provide OpenTelemetry
     * specific functionality for agents. It manages spans and contexts to trace and monitor
     * the lifecycle of agent executions, nodes, LLM calls, and tool invocations.
     *
     * This class handles:
     * - Initialization and configuration of OpenTelemetry agents.
     * - Interception and tracing of agent lifecycle events such as agent start, finish,
     *   run errors, and various activities like node execution, LLM calls, and tool calls.
     * - Management of spans and contexts for monitoring and lifecycle completion.
     *
     * The implementation includes private utility methods for ensuring spans are handled
     * correctly and resources are properly released.
     */
    public companion object Feature : AIAgentFeature<OpenTelemetryConfig, OpenTelemetry> {

        private val logger = KotlinLogging.logger { }

        private val spans = ConcurrentHashMap<String, TraceSpanBase>()

        override val key: AIAgentStorageKey<OpenTelemetry> = AIAgentStorageKey("agents-features-opentelemetry")

        override fun createInitialConfig(): OpenTelemetryConfig {
            return OpenTelemetryConfig()
        }

        override fun install(
            config: OpenTelemetryConfig,
            pipeline: AIAgentPipeline
        ) {
            val interceptContext = InterceptContext(this, OpenTelemetry())
            val tracer = config.tracer

            //region Agent

            pipeline.interceptBeforeAgentStarted(interceptContext) {

                val agentId = agent.id
                val spanId = AgentSpan.createId(agentId)

                val agentSpan = spans.getOrPut(spanId) {
                    AgentSpan(tracer = tracer, agentId = agent.id).also { it.start() }
                } as AgentSpan

                val agentRunSpan = AgentRunSpan(
                    tracer = tracer,
                    parentSpan = agentSpan,
                    sessionId = sessionId,
                    strategyName = strategy.name
                )

                agentRunSpan.start()
                spans[agentRunSpan.spanId] = agentRunSpan
            }

            pipeline.interceptAgentFinished(interceptContext) { event ->
                endUnfinishedSpans(agentId = event.agentId, sessionId = event.sessionId)

                // Find an existing agent run span
                val agentRunSpanId = AgentRunSpan.createId(agentId = event.agentId, sessionId = event.sessionId)

                spans.remove(agentRunSpanId)?.let { it as? AgentRunSpan }?.let { span: AgentRunSpan ->
                    span.end(
                        completed = true,
                        result = event.result ?: "null",
                        statusCode = StatusCode.OK
                    )
                }
            }

            pipeline.interceptAgentRunError(interceptContext) { event ->
                endUnfinishedSpans(agentId = event.agentId, sessionId = event.sessionId)

                // Find an existing agent run span
                val agentRunSpanId = AgentRunSpan.createId(agentId = event.agentId, sessionId = event.sessionId)

                spans.remove(agentRunSpanId)?.let { it as? AgentRunSpan }?.let { span: AgentRunSpan ->
                    span.end(
                        completed = false,
                        result = event.throwable.message ?: "null",
                        statusCode = StatusCode.ERROR
                    )
                }
            }

            //endregion Agent

            //region Node

            pipeline.interceptBeforeNode(interceptContext) { event ->

                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create node span due to missing agent run info in context")

                val parentSpanId = AgentRunSpan.createId(agentId = agentRunInfoElement.agentId, sessionId = agentRunInfoElement.sessionId)
                val parentSpan = spans[parentSpanId] as AgentRunSpan

                val nodeExecuteSpan = NodeExecuteSpan(
                    tracer = tracer,
                    parentSpan = parentSpan,
                    nodeName = event.node.name,
                )

                nodeExecuteSpan.start()
                spans[nodeExecuteSpan.spanId] = nodeExecuteSpan
            }

            pipeline.interceptAfterNode(interceptContext) { event ->

                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to end node span due to missing agent run info in context")

                // Find an existing node span
                val nodeExecuteSpanId = NodeExecuteSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    sessionId = agentRunInfoElement.sessionId,
                    nodeName = event.node.name
                )

                spans.remove(nodeExecuteSpanId)?.let { it as? NodeExecuteSpan }?.let { span: NodeExecuteSpan ->
                    span.end()
                }
            }

            //endregion Node

            //region LLM Call

            pipeline.interceptBeforeLLMCall(interceptContext) { event ->

                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create LLM call span due to missing agent run info in context")

                val nodeInfoElement = currentCoroutineContext().getNodeInfoElement()
                    ?: error("Unable to create LLM call span due to missing node info in context")

                val parentSpanId = NodeExecuteSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    sessionId = agentRunInfoElement.sessionId,
                    nodeName = nodeInfoElement.nodeName
                )

                val parentSpan = spans[parentSpanId] as NodeExecuteSpan

                val llmCallSpan = LLMCallSpan(
                    tracer = tracer,
                    parentSpan = parentSpan,
                    model = event.model.id,
                    temperature = event.prompt.params.temperature ?: 0.0,
                    promptId = event.prompt.id
                )

                llmCallSpan.start()
                spans[llmCallSpan.spanId] = llmCallSpan
            }

            pipeline.interceptAfterLLMCall(interceptContext) { event ->

                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create LLM call span due to missing agent run info in context")

                val nodeInfoElement = currentCoroutineContext().getNodeInfoElement()
                    ?: error("Unable to create LLM call span due to missing node info in context")

                // Find an existing LLM call span
                val llmCallSpanId = LLMCallSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    sessionId = agentRunInfoElement.sessionId,
                    nodeName = nodeInfoElement.nodeName,
                    promptId = event.prompt.id
                )

                spans.remove(llmCallSpanId)?.let { it as? LLMCallSpan }?.let { span: LLMCallSpan ->
                    span.end(
                        responses = event.responses,
                        statusCode = StatusCode.OK
                    )
                }
            }

            //endregion LLM Call

            //region Tool Call

            pipeline.interceptToolCall(interceptContext) { event ->

                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create tool call span due to missing agent run info in context")

                val nodeInfoElement = currentCoroutineContext().getNodeInfoElement()
                    ?: error("Unable to create tool call span due to missing node info in context")

                val parentSpanId = NodeExecuteSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    sessionId = agentRunInfoElement.sessionId,
                    nodeName = nodeInfoElement.nodeName
                )

                val parentSpan = spans[parentSpanId] as NodeExecuteSpan

                val toolCallSpan = ToolCallSpan(
                    tracer = tracer,
                    parentSpan = parentSpan,
                    tool = event.tool,
                    toolArgs = event.toolArgs,
                )

                toolCallSpan.start()
                spans[toolCallSpan.spanId] = toolCallSpan
            }

            pipeline.interceptToolCallResult(interceptContext) { event ->

                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create tool call span due to missing agent run info in context")

                val nodeInfoElement = currentCoroutineContext().getNodeInfoElement()
                    ?: error("Unable to create tool call span due to missing node info in context")

                // Find an existing Tool call span
                val toolCallSpanId = ToolCallSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    sessionId = agentRunInfoElement.sessionId,
                    nodeName = nodeInfoElement.nodeName,
                    toolName = event.tool.name
                )

                spans.remove(toolCallSpanId)?.let { it as? ToolCallSpan }?.let { span: ToolCallSpan ->
                    span.end(
                        result = event.result?.toStringDefault() ?: "null",
                        statusCode = StatusCode.OK
                    )
                }
            }

            pipeline.interceptToolCallFailure(interceptContext) { event ->

                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create tool call span due to missing agent run info in context")

                val nodeInfoElement = currentCoroutineContext().getNodeInfoElement()
                    ?: error("Unable to create tool call span due to missing node info in context")

                // Find an existing Tool call span
                val toolCallSpanId = ToolCallSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    sessionId = agentRunInfoElement.sessionId,
                    nodeName = nodeInfoElement.nodeName,
                    toolName = event.tool.name
                )

                spans.remove(toolCallSpanId)?.let { it as? ToolCallSpan }?.let { span: ToolCallSpan ->
                    span.end(
                        result = event.throwable.message ?: "null",
                        statusCode = StatusCode.ERROR
                    )
                }
            }

            //endregion Tool Call
        }

        //region Private Methods

        private fun endUnfinishedSpans(agentId: String, sessionId: String) {
            spans.entries
                .filter { (id, _) -> id != AgentRunSpan.createId(agentId, sessionId) }
                .forEach { (id, span) ->
                    logger.warn { "Force close span with id: $id" }
                    span.endInternal(attributes = emptyList(), StatusCode.UNSET)
                }
        }

        //endregion Private Methods
    }
}
