package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.context.element.getAgentRunInfoElement
import ai.koog.agents.core.agent.context.element.getNodeInfoElement
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.features.opentelemetry.feature.span.*
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.currentCoroutineContext

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
            val spanStorage = SpanStorage()

            Runtime.getRuntime().addShutdownHook(Thread {
                println("SD -- Dispose. Force closing all spans")
                spanStorage.endUnfinishedSpans()
            })

            //region Agent

            pipeline.interceptBeforeAgentStarted(interceptContext) {

                // Agent span
                val agentSpanId = AgentSpan.createId(agent.id)

                val agentSpan = spanStorage.getOrPutSpan(agentSpanId) {
                    AgentSpan(tracer = tracer, agentId = agent.id).also { it.start() }
                }

                // Agent Run span
                val agentRunSpan = AgentRunSpan(
                    tracer = tracer,
                    parentSpan = agentSpan,
                    sessionId = sessionId,
                    strategyName = strategy.name
                )

                agentRunSpan.start()
                spanStorage.addSpan(agentRunSpan.spanId, agentRunSpan)
            }

            pipeline.interceptAgentFinished(interceptContext) { event ->
                spanStorage.endUnfinishedAgentRunSpans(agentId = event.agentId, sessionId = event.sessionId)

                // Find an existing agent run span
                val agentRunSpanId = AgentRunSpan.createId(agentId = event.agentId, sessionId = event.sessionId)

                spanStorage.removeSpan<AgentRunSpan>(agentRunSpanId)?.let { span: AgentRunSpan ->
                    span.end(
                        completed = true,
                        result = event.result ?: "null",
                        statusCode = StatusCode.OK
                    )
                }
            }

            pipeline.interceptAgentRunError(interceptContext) { event ->
                spanStorage.endUnfinishedAgentRunSpans(agentId = event.agentId, sessionId = event.sessionId)

                // Find an existing agent run span
                val agentRunSpanId = AgentRunSpan.createId(agentId = event.agentId, sessionId = event.sessionId)

                spanStorage.removeSpan<AgentRunSpan>(agentRunSpanId)?.let { span: AgentRunSpan ->
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
                val parentSpan = spanStorage.getSpanOrThrow<AgentRunSpan>(parentSpanId)

                val nodeExecuteSpan = NodeExecuteSpan(
                    tracer = tracer,
                    parentSpan = parentSpan,
                    nodeName = event.node.name,
                )

                nodeExecuteSpan.start()
                spanStorage.addSpan(nodeExecuteSpan.spanId, nodeExecuteSpan)
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

                spanStorage.removeSpan<NodeExecuteSpan>(nodeExecuteSpanId)?.let { span: NodeExecuteSpan ->
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

                val parentSpan = spanStorage.getSpanOrThrow<NodeExecuteSpan>(parentSpanId)

                val llmCallSpan = LLMCallSpan(
                    tracer = tracer,
                    parentSpan = parentSpan,
                    model = event.model.id,
                    temperature = event.prompt.params.temperature ?: 0.0,
                    promptId = event.prompt.id
                )

                llmCallSpan.start()
                spanStorage.addSpan(llmCallSpan.spanId, llmCallSpan)
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

                spanStorage.removeSpan<LLMCallSpan>(llmCallSpanId)?.let { span: LLMCallSpan ->
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

                val parentSpan = spanStorage.getSpanOrThrow<NodeExecuteSpan>(parentSpanId)

                val toolCallSpan = ToolCallSpan(
                    tracer = tracer,
                    parentSpan = parentSpan,
                    tool = event.tool,
                    toolArgs = event.toolArgs,
                )

                toolCallSpan.start()
                spanStorage.addSpan(toolCallSpan.spanId, toolCallSpan)
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

                spanStorage.removeSpan<ToolCallSpan>(toolCallSpanId)?.let { span: ToolCallSpan ->
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

                spanStorage.removeSpan<ToolCallSpan>(toolCallSpanId)?.let { span: ToolCallSpan ->
                    span.end(
                        result = event.throwable.message ?: "null",
                        statusCode = StatusCode.ERROR
                    )
                }
            }

            //endregion Tool Call
        }
    }
}
