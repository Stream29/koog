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
                spanStorage.endUnfinishedSpans()
            })

            //region Agent

            pipeline.interceptBeforeAgentStarted(interceptContext) { eventContext ->

                // Agent span
                val agentSpanId = AgentSpan.createId(eventContext.agent.id)

                val agentSpan = spanStorage.getOrPutSpan(agentSpanId) {
                    AgentSpan(tracer = tracer, agentId = eventContext.agent.id).also { it.start() }
                }

                // Agent Run span
                val agentRunSpan = AgentRunSpan(
                    tracer = tracer,
                    parentSpan = agentSpan,
                    runId = eventContext.runId,
                    strategyName = eventContext.strategy.name
                )

                agentRunSpan.start()
                spanStorage.addSpan(agentRunSpan.spanId, agentRunSpan)
            }

            pipeline.interceptAgentFinished(interceptContext) { eventContext ->
                spanStorage.endUnfinishedAgentRunSpans(agentId = eventContext.agentId, runId = eventContext.runId)

                // Find an existing agent run span
                val agentRunSpanId = AgentRunSpan.createId(agentId = eventContext.agentId, runId = eventContext.runId)

                spanStorage.removeSpan<AgentRunSpan>(agentRunSpanId)?.let { span: AgentRunSpan ->
                    span.end(
                        completed = true,
                        result = eventContext.result?.toString() ?: "null",
                        statusCode = StatusCode.OK
                    )
                }
            }

            pipeline.interceptAgentRunError(interceptContext) { eventContext ->
                spanStorage.endUnfinishedAgentRunSpans(agentId = eventContext.agentId, runId = eventContext.runId)

                // Find an existing agent run span
                val agentRunSpanId = AgentRunSpan.createId(agentId = eventContext.agentId, runId = eventContext.runId)

                spanStorage.removeSpan<AgentRunSpan>(agentRunSpanId)?.let { span: AgentRunSpan ->
                    span.end(
                        completed = false,
                        result = eventContext.throwable.message ?: "null",
                        statusCode = StatusCode.ERROR
                    )
                }
            }

            pipeline.interceptAgentBeforeClosed(interceptContext) { eventContext ->
                val agentSpanId = AgentSpan.createId(eventContext.agentId)

                spanStorage.removeSpan<AgentSpan>(agentSpanId)?.let { span: AgentSpan ->
                    span.end()
                }
            }

            //endregion Agent

            //region Node

            pipeline.interceptBeforeNode(interceptContext) { eventContext ->

                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create node span due to missing agent run info in context")

                val parentSpanId = AgentRunSpan.createId(agentId = agentRunInfoElement.agentId, runId = agentRunInfoElement.runId)
                val parentSpan = spanStorage.getSpanOrThrow<AgentRunSpan>(parentSpanId)

                val nodeExecuteSpan = NodeExecuteSpan(
                    tracer = tracer,
                    parentSpan = parentSpan,
                    nodeName = eventContext.node.name,
                )

                nodeExecuteSpan.start()
                spanStorage.addSpan(nodeExecuteSpan.spanId, nodeExecuteSpan)
            }

            pipeline.interceptAfterNode(interceptContext) { eventContext ->

                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to end node span due to missing agent run info in context")

                // Find an existing node span
                val nodeExecuteSpanId = NodeExecuteSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = eventContext.node.name
                )

                spanStorage.removeSpan<NodeExecuteSpan>(nodeExecuteSpanId)?.let { span: NodeExecuteSpan ->
                    span.end()
                }
            }

            //endregion Node

            //region LLM Call

            pipeline.interceptBeforeLLMCall(interceptContext) { eventContext ->

                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create LLM call span due to missing agent run info in context")

                val nodeInfoElement = currentCoroutineContext().getNodeInfoElement()
                    ?: error("Unable to create LLM call span due to missing node info in context")

                val parentSpanId = NodeExecuteSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName
                )

                val parentSpan = spanStorage.getSpanOrThrow<NodeExecuteSpan>(parentSpanId)

                val llmCallSpan = LLMCallSpan(
                    tracer = tracer,
                    parentSpan = parentSpan,
                    promptId = eventContext.prompt.id,
                    model = eventContext.model,
                    tools = eventContext.tools,
                    temperature = eventContext.prompt.params.temperature ?: 0.0
                )

                llmCallSpan.start()
                spanStorage.addSpan(llmCallSpan.spanId, llmCallSpan)
            }

            pipeline.interceptAfterLLMCall(interceptContext) { eventContext ->

                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create LLM call span due to missing agent run info in context")

                val nodeInfoElement = currentCoroutineContext().getNodeInfoElement()
                    ?: error("Unable to create LLM call span due to missing node info in context")

                // Find an existing LLM call span
                val llmCallSpanId = LLMCallSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName,
                    promptId = eventContext.prompt.id
                )

                spanStorage.removeSpan<LLMCallSpan>(llmCallSpanId)?.let { span: LLMCallSpan ->
                    span.end(
                        responses = eventContext.responses,
                        statusCode = StatusCode.OK
                    )
                }
            }

            pipeline.interceptBeforeExecuteMultipleChoices(interceptContext) { eventContext ->
                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create LLM call for multiple choice span due to missing agent run info in context")

                val nodeInfoElement = currentCoroutineContext().getNodeInfoElement()
                    ?: error("Unable to create LLM call for multiple choice span due to missing node info in context")

                val parentSpanId = NodeExecuteSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName
                )

                val parentSpan = spanStorage.getSpanOrThrow<NodeExecuteSpan>(parentSpanId)

                val llmCallMultipleChoiceSpan = LLMCallMultipleChoiceSpan(
                    tracer = tracer,
                    parentSpan = parentSpan,
                    model = eventContext.model.id,
                    temperature = eventContext.prompt.params.temperature ?: 0.0,
                    promptId = eventContext.prompt.id,
                    tools = eventContext.tools
                )

                llmCallMultipleChoiceSpan.start()
                spanStorage.addSpan(llmCallMultipleChoiceSpan.spanId, llmCallMultipleChoiceSpan)
            }

            pipeline.interceptAfterExecuteMultipleChoices(interceptContext) { eventContext ->

            }

            //endregion LLM Call

            //region Tool Call

            pipeline.interceptToolCall(interceptContext) { eventContext ->

                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create tool call span due to missing agent run info in context")

                val nodeInfoElement = currentCoroutineContext().getNodeInfoElement()
                    ?: error("Unable to create tool call span due to missing node info in context")

                val parentSpanId = NodeExecuteSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName
                )

                val parentSpan = spanStorage.getSpanOrThrow<NodeExecuteSpan>(parentSpanId)

                val toolCallSpan = ToolCallSpan(
                    tracer = tracer,
                    parentSpan = parentSpan,
                    tool = eventContext.tool,
                    toolArgs = eventContext.toolArgs,
                )

                toolCallSpan.start()
                spanStorage.addSpan(toolCallSpan.spanId, toolCallSpan)
            }

            pipeline.interceptToolCallResult(interceptContext) { eventContext ->

                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create tool call span due to missing agent run info in context")

                val nodeInfoElement = currentCoroutineContext().getNodeInfoElement()
                    ?: error("Unable to create tool call span due to missing node info in context")

                // Find an existing Tool call span
                val toolCallSpanId = ToolCallSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName,
                    toolName = eventContext.tool.name
                )

                spanStorage.removeSpan<ToolCallSpan>(toolCallSpanId)?.let { span: ToolCallSpan ->
                    span.end(
                        result = eventContext.result?.toStringDefault() ?: "null",
                        statusCode = StatusCode.OK
                    )
                }
            }

            pipeline.interceptToolCallFailure(interceptContext) { eventContext ->

                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create tool call span due to missing agent run info in context")

                val nodeInfoElement = currentCoroutineContext().getNodeInfoElement()
                    ?: error("Unable to create tool call span due to missing node info in context")

                // Find an existing Tool call span
                val toolCallSpanId = ToolCallSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName,
                    toolName = eventContext.tool.name
                )

                spanStorage.removeSpan<ToolCallSpan>(toolCallSpanId)?.let { span: ToolCallSpan ->
                    span.end(
                        result = eventContext.throwable.message ?: "null",
                        statusCode = StatusCode.ERROR
                    )
                }
            }

            //endregion Tool Call
        }
    }
}
