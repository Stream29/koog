package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.context.element.getAgentRunInfoElement
import ai.koog.agents.core.agent.context.element.getNodeInfoElement
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.event.AssistantMessageEvent
import ai.koog.agents.features.opentelemetry.event.ChoiceEvent
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.event.SystemMessageEvent
import ai.koog.agents.features.opentelemetry.event.ToolMessageEvent
import ai.koog.agents.features.opentelemetry.event.UserMessageEvent
import ai.koog.agents.features.opentelemetry.span.CreateAgentSpan
import ai.koog.agents.features.opentelemetry.span.InferenceSpan
import ai.koog.agents.features.opentelemetry.span.NodeExecuteSpan
import ai.koog.agents.features.opentelemetry.span.ExecuteToolSpan
import ai.koog.agents.features.opentelemetry.span.InvokeAgentSpan
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.currentCoroutineContext
import kotlin.uuid.ExperimentalUuidApi

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

        private val logger = KotlinLogging.logger {  }

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
            val spanProcessor = SpanProcessor(tracer)

            // Stop all unfinished spans on a process finish to report them
            Runtime.getRuntime().addShutdownHook(Thread {
                if (spanProcessor.spansCount > 1) {
                    logger.warn { "Unfinished spans detected. Please check your code for unclosed spans." }
                }

                logger.debug { "Closing unended OpenTelemetry spans on process shutdown (size: ${spanProcessor.spansCount})" }
                spanProcessor.endUnfinishedSpans()
            })

            //region Agent

            pipeline.interceptBeforeAgentStarted(interceptContext) { eventContext ->

                // Check if CreateAgentSpan is already added (when running the same agent >= 1 times)
                val createAgentSpanId = CreateAgentSpan.createId(eventContext.agent.id)

                val createAgentSpan = spanProcessor.getSpan(createAgentSpanId) ?: run {
                    val span = CreateAgentSpan(
                        model = eventContext.agent.agentConfig.model,
                        agentId = eventContext.agent.id
                    )

                    spanProcessor.startSpan(span)
                    span
                }

                // Create InvokeAgentSpan
                val invokeAgentSpan = InvokeAgentSpan(
                    parent = createAgentSpan,
                    provider = eventContext.agent.agentConfig.model.provider,
                    agentId = eventContext.agent.id,
                    runId = eventContext.runId,
                    strategyName = eventContext.strategy.name
                )

                spanProcessor.startSpan(invokeAgentSpan)
            }

            pipeline.interceptAgentFinished(interceptContext) { eventContext ->

                // Make sure all spans inside InvokeAgentSpan are finished
                spanProcessor.endUnfinishedInvokeAgentSpans(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId
                )

                // Find current InvokeAgentSpan
                val invokeAgentSpanId = InvokeAgentSpan.createId(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId
                )
                spanProcessor.endSpan(spanId = invokeAgentSpanId)
            }

            pipeline.interceptAgentRunError(interceptContext) { eventContext ->

                // Make sure all spans inside InvokeAgentSpan are finished
                spanProcessor.endUnfinishedInvokeAgentSpans(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId
                )

                // Finish current InvokeAgentSpan
                val invokeAgentSpanId = InvokeAgentSpan.createId(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId
                )

                val finishAttributes = buildList {
                    add(SpanAttributes.Response.FinishReasons(listOf(SpanAttributes.Response.FinishReasonType.Error)))
                }

                spanProcessor.endSpan(
                    spanId = invokeAgentSpanId,
                    attributes = finishAttributes,
                    spanEndStatus = SpanEndStatus(code = StatusCode.ERROR, description = eventContext.throwable.message)
                )
            }

            pipeline.interceptAgentBeforeClosed(interceptContext) { eventContext ->
                val agentSpanId = CreateAgentSpan.createId(agentId = eventContext.agentId)
                spanProcessor.endSpan(agentSpanId)
            }

            //endregion Agent

            //region Node

            pipeline.interceptBeforeNode(interceptContext) { eventContext ->

                // Get current InvokeAgentSpan
                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create node span due to missing agent run info in context")

                val invokeAgentSpanId = InvokeAgentSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId
                )
                val invokeAgentSpan = spanProcessor.getSpanOrThrow<InvokeAgentSpan>(invokeAgentSpanId)

                // Create NodeExecuteSpan
                val nodeExecuteSpan = NodeExecuteSpan(
                    parent = invokeAgentSpan,
                    runId = eventContext.context.runId,
                    nodeName = eventContext.node.name,
                )

                spanProcessor.startSpan(nodeExecuteSpan)
            }

            pipeline.interceptAfterNode(interceptContext) { eventContext ->

                // Find current NodeExecuteSpan
                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to end node span due to missing agent run info in context")

                // Finish existing NodeExecuteSpan
                val nodeExecuteSpanId = NodeExecuteSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = eventContext.node.name
                )

                spanProcessor.endSpan(nodeExecuteSpanId,)
            }

            //endregion Node

            //region LLM Call

            pipeline.interceptBeforeLLMCall(interceptContext) { eventContext ->

                // Get current NodeExecuteSpan
                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create LLM call span due to missing agent run info in context")

                val nodeInfoElement = currentCoroutineContext().getNodeInfoElement()
                    ?: error("Unable to create LLM call span due to missing node info in context")

                val nodeExecuteSpanId = NodeExecuteSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName
                )

                val nodeExecuteSpan = spanProcessor.getSpanOrThrow<NodeExecuteSpan>(nodeExecuteSpanId)

                val provider = eventContext.model.provider
                val runId = eventContext.runId
                val model = eventContext.model
                val temperature = eventContext.prompt.params.temperature ?: 0.0
                val promptId = eventContext.prompt.id
                val tools = eventContext.tools

                val inferenceSpan = InferenceSpan(
                    provider = provider,
                    parent = nodeExecuteSpan,
                    runId = runId,
                    model = model,
                    temperature = temperature,
                    promptId = promptId,
                    tools = tools
                )

                // Start span
                spanProcessor.startSpan(inferenceSpan)

                // Add events to the InferenceSpan after span is created
                val lastMessage = eventContext.prompt.messages.lastOrNull()

                val events: List<GenAIAgentEvent> = lastMessage?.let { message ->
                    buildList {
                        when (message) {
                            is Message.User -> add(UserMessageEvent(provider, message, verbose = config.verbose))
                            is Message.System -> add(SystemMessageEvent(provider, message, verbose = config.verbose))
                            is Message.Assistant -> add(AssistantMessageEvent(provider, message, verbose = config.verbose))
                            is Message.Tool.Result -> add(ToolMessageEvent(provider, message, verbose = config.verbose))
                            else -> {}
                        }
                    }
                } ?: emptyList()

                inferenceSpan.addEvents(events)
            }

            pipeline.interceptAfterLLMCall(interceptContext) { eventContext ->

                // Find current InferenceSpan
                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create LLM call span due to missing agent run info in context")

                val nodeInfoElement = currentCoroutineContext().getNodeInfoElement()
                    ?: error("Unable to create LLM call span due to missing node info in context")

                val inferenceSpanId = InferenceSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName,
                    promptId = eventContext.prompt.id
                )

                val inferenceSpan = spanProcessor.getSpanOrThrow<InferenceSpan>(inferenceSpanId)

                val provider = eventContext.model.provider

                // Add events to the InferenceSpan before finishing the span
                val lastMessage = eventContext.prompt.messages.lastOrNull()

                val events: List<GenAIAgentEvent> = lastMessage?.let { message ->
                    buildList {
                        when (message) {
                            is Message.Response -> add(ChoiceEvent(provider, message, config.verbose))
                            else -> {}
                        }
                    }
                } ?: emptyList()

                inferenceSpan.addEvents(events)

                // Stop InferenceSpan
                spanProcessor.endSpan(inferenceSpanId)
            }

            //endregion LLM Call

            //region Tool Call

            pipeline.interceptToolCall(interceptContext) { eventContext ->

                // Get current NodeExecuteSpan
                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create tool call span due to missing agent run info in context")

                val nodeInfoElement = currentCoroutineContext().getNodeInfoElement()
                    ?: error("Unable to create tool call span due to missing node info in context")

                val nodeExecutionSpanId = NodeExecuteSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName
                )

                val nodeExecuteSpan = spanProcessor.getSpanOrThrow<NodeExecuteSpan>(nodeExecutionSpanId)

                val toolCallSpan = ExecuteToolSpan(
                    tracer = tracer,
                    parent = nodeExecuteSpan,
                    runId = eventContext.runId,
                    tool = eventContext.tool,
                    toolArgs = eventContext.toolArgs,
                )

                @OptIn(ExperimentalUuidApi::class)
                toolCallSpan.start()

                spanStorage.addSpan(toolCallSpan.spanId, toolCallSpan)
            }

            pipeline.interceptToolCallResult(interceptContext) { eventContext ->

                val agentRunInfoElement = currentCoroutineContext().getAgentRunInfoElement()
                    ?: error("Unable to create tool call span due to missing agent run info in context")

                val nodeInfoElement = currentCoroutineContext().getNodeInfoElement()
                    ?: error("Unable to create tool call span due to missing node info in context")

                // Find an existing Tool call span
                val toolCallSpanId = ExecuteToolSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName,
                    toolName = eventContext.tool.name
                )

                spanStorage.removeSpan<ExecuteToolSpan>(toolCallSpanId)?.let { span: ExecuteToolSpan ->
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
                val toolCallSpanId = ExecuteToolSpan.createId(
                    agentId = agentRunInfoElement.agentId,
                    runId = agentRunInfoElement.runId,
                    nodeName = nodeInfoElement.nodeName,
                    toolName = eventContext.tool.name
                )

                spanStorage.removeSpan<ExecuteToolSpan>(toolCallSpanId)?.let { span: ExecuteToolSpan ->
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
