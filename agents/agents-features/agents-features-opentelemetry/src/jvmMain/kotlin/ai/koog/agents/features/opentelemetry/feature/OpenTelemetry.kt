package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.context.element.AgentRunInfoContextElement
import ai.koog.agents.core.agent.context.element.NodeInfoContextElement
import ai.koog.agents.core.agent.context.element.getNodeInfoElement
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.features.opentelemetry.feature.span.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import kotlinx.coroutines.currentCoroutineContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.uuid.ExperimentalUuidApi

/**
 * TODO: SD -- fix
 */
public class OpenTelemetry {

    /**
     * TODO: SD -- fix
     */
    @OptIn(ExperimentalUuidApi::class)
    public companion object Feature : AIAgentFeature<OpenTelemetryConfig, OpenTelemetry> {

        private val logger = KotlinLogging.logger { }

        private val spans = ConcurrentHashMap<String, Span>()
        private val spans2 = ConcurrentHashMap<String, TraceSpanBase>()

        private val contexts = ConcurrentHashMap<String, Context>()

        override val key: AIAgentStorageKey<OpenTelemetry> = AIAgentStorageKey("agents-features-opentelemetry")

        override fun createInitialConfig(): OpenTelemetryConfig {
            return OpenTelemetryConfig()
        }

        override fun install(
            config: OpenTelemetryConfig,
            pipeline: AIAgentPipeline
        ) {

            // Setup tracer
            val tracer = config.tracer
            val propagator = config.sdk.propagators

            // Root spans
            // TODO: SD -- fix the issue with running agent twice and get root span closed
            val rootSpanId = SpanEvent.AGENT.id
            val rootSpan = tracer.spanBuilder(rootSpanId).startSpan()
            val rootScope = rootSpan.makeCurrent()
            val rootContext = Context.current()

            // Store spans for later use
            spans[rootSpanId] = rootSpan
            contexts[rootSpanId] = rootContext

            // Setup feature
            val interceptContext = InterceptContext(this, OpenTelemetry())

            //region Agent

            pipeline.interceptBeforeAgentStarted(interceptContext) {

                val agentId = agent.id
                val spanId = SpanEvent.Agent(agentId = agentId).id

                val agentSpan = spans2.getOrPut(spanId) { AgentSpan(tracer = tracer, agentId = agent.id) } as AgentSpan
                agentSpan.start()

                val agentRunSpan = AgentRunSpan(tracer = tracer, parentSpan = agentSpan, agentId = agentId, parentContext = rootContext )
                agentRunSpan.start(sessionId = sessionId, strategyName = strategy.name)

                spans2[agentRunSpan.spanEvent.id] = agentRunSpan


                val agentSpanContext = agentSpan.start(agent.id, sessionId, strategy.name)

                val agentSpanId = SpanEvent.getAgentRunId(sessionId = sessionId)

                val span = tracer.spanBuilder(agentSpanId)
                    .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .setParent(parentContext)
                    .setAttribute("get_ai.system", agent.agentConfig.model.provider.id)
                    .setAttribute("gen_ai.operation.name", GenAIAttribute.Operation.OperationName.INVOKE_AGENT.id)
                    .setAttribute("gen_ai.agent.id", agent.id)
                    .setAttribute("gen_ai.agent.sessionId", sessionId)
                    .setAttribute("gen_ai.agent.strategy", strategy.name)
                    .setAttribute("gen_ai.agent.completed", false)
                    .startSpan()

                val spanContext = span.storeInContext(parentContext)

                spans[agentSpanId] = span
                contexts[agentSpanId] = spanContext
            }

            pipeline.interceptAgentFinished(interceptContext) { event ->

                val agentSpanId = SpanEvent.getAgentRunId(sessionId = event.sessionId)

                spans.get(agentSpanId)?.let { span ->
                    span.setAttribute("gen_ai.agent.result", event.result ?: "UNDEFINED")
                    span.setAttribute("gen_ai.agent.completed", true)
                    span.setStatus(StatusCode.OK)
                    span.end()

                    spans.remove(agentSpanId)
                    contexts.remove(agentSpanId)
                }

                endUnfinishedSpans()

                rootScope.close()
                rootSpan.end()
            }

            pipeline.interceptAgentRunError(interceptContext) { event ->
                // Close agent span in case of error as well
                val agentSpanId = SpanEvent.getAgentRunId(sessionId = event.sessionId)



            }

            //endregion Agent

            //region Strategy

            pipeline.interceptStrategyStarted(interceptContext) {
                val agentSpanId = SpanEvent.getAgentRunId(sessionId)
                val parentContext = contexts.get(agentSpanId) ?: Context.current()

                val id = SpanEvent.getStrategyRunId(sessionId = sessionId)

                val span = tracer.spanBuilder(id)
                .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .setParent(parentContext)
                .setAttribute("gen_ai.operation.name", OperationName.RUN_STRATEGY.id)
                .setAttribute("gen_ai.agent.sessionId", sessionId)
                .setAttribute("gen_ai.strategy.name", strategy.name)
                .startSpan()

                spans[id] = span
                contexts[id] = span.storeInContext(parentContext)
            }

            pipeline.interceptStrategyFinished(interceptContext) { result ->
                val strategySpanId = SpanEvent.getStrategyRunId(sessionId = sessionId)
                spans.get(strategySpanId)?.let { span ->
                    span.end()

                    spans.remove(strategySpanId)
                    contexts.remove(strategySpanId)
                }
            }

            //endregion Strategy

            //region Node

            pipeline.interceptBeforeNode(interceptContext) { eventContext ->

                val strategySpanId = SpanEvent.getStrategyRunId(sessionId = eventContext.context.sessionId)

                val parentContext = contexts.get(strategySpanId) ?: Context.current()

                // TODO: SD -- fix case when nodeName is not in the context
                val id = SpanEvent.getNodeExecutionId(
                    sessionId = eventContext.context.sessionId,
                    nodeId = eventContext.node.name
                )

                val span = tracer.spanBuilder(id)
                    .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .setParent(parentContext)
                    .setAttribute("get_ai.system", eventContext.context.sessionId)
                    .setAttribute("gen_ai.operation.name", OperationName.EXECUTE_NODE.id)
                    .startSpan()

                spans[id] = span
                contexts[id] = span.storeInContext(parentContext)
            }

            pipeline.interceptAfterNode(interceptContext) { eventContext ->

                val nodeSpanId = SpanEvent.getNodeExecutionId(sessionId = eventContext.agentContext.sessionId, nodeId = node.name)

                spans.get(nodeSpanId)?.let { span ->
                    span.end()

                    spans.remove(nodeSpanId)
                    contexts.remove(nodeSpanId)
                }
            }

            //endregion Node

            //region LLM Call

            pipeline.interceptBeforeLLMCall(interceptContext) { eventContext ->

//                coroutineContext.getOpenTelemetryContext()

                val nodeNameFromContext = coroutineContext[NodeInfoContextElement.Key]?.nodeName
                println("SD -- BeforeLLMCall. node name: $nodeName")
                println("SD -- BeforeLLMCall. node name from context: $nodeNameFromContext")

                val nodeSpanId = SpanEvent.getNodeExecutionId(sessionId = sessionId, nodeId = nodeName)
                val parentContext = contexts[nodeSpanId] ?: Context.current()

                val id = SpanEvent.getLLMCallId(sessionId = sessionId, nodeId = nodeName)

                val span = tracer.spanBuilder(id)
                    .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .setParent(parentContext)
                    .setAttribute("gen_ai.system", sessionId)
                    .setAttribute("gen_ai.operation.name", OperationName.CHAT.id)
                    .setAttribute("gen_ai.model", model.id)
                    .startSpan()

                spans.put(id, span)
                contexts.put(id, span.storeInContext(parentContext))
            }

            pipeline.interceptAfterLLMCall(interceptContext) { eventContext ->

                // TODO: SD -- handle the case when no nodeName found
                val nodeNameFromContext = coroutineContext[NodeInfoContextElement.Key]?.nodeName ?: ""
                println("SD -- AfterLLMCall. node name: $nodeName")
                println("SD -- AfterLLMCall. node name from context: $nodeNameFromContext")

                val llmCallSpanId = SpanEvent.getLLMCallId(sessionId = sessionId, nodeId = nodeName)

                spans.get(llmCallSpanId)?.let { span ->
                    response?.toString()?.let { result ->
                        span.setAttribute("gen_ai.llm.response", result.take(1000)) // Truncate long responses
                    }

                    span.end()

                    spans.remove(llmCallSpanId)
                    contexts.remove(llmCallSpanId)
                }
            }

            //endregion LLM Call

            //region Tool Call

            pipeline.interceptToolCall(interceptContext) { eventContext ->

                val nodeNameElement = currentCoroutineContext().getNodeInfoElement()
                println("SD -- ToolCall. node name from context: ${nodeNameElement?.nodeName}")

                val sessionIdFromContext = currentCoroutineContext()[AgentRunInfoContextElement.Key]?.sessionId
                println("SD -- ToolCall. session id from context: $sessionIdFromContext")

                val nodeSpanId = SpanEvent.getNodeExecutionId(sessionId = sessionId, nodeId = nodeName)
                val parentContext = contexts[nodeSpanId] ?: Context.current()

                val id = SpanEvent.getToolCallId(sessionId = sessionId, nodeId = nodeName)

                val span = tracer.spanBuilder(id)
                    .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .setParent(parentContext)
                    .setAttribute("gen_ai.system", sessionId)
                    .setAttribute("gen_ai.operation.name", OperationName.EXECUTE_TOOL.id)
                    .setAttribute("gen_ai.tool.name", tool.name)
                    .setAttribute("gen_ai.tool.args", toolArgs.toString())
                    .startSpan()

                spans.put(id, span)
                contexts.put(id, span.storeInContext(parentContext))
            }

            pipeline.interceptToolCallResult(interceptContext) { eventContext ->
                val nodeNameFromContext = coroutineContext[NodeInfoContextElement.Key]?.nodeName
                val sessionIdFromContext = coroutineContext[AgentRunInfoContextElement.Key]?.sessionId
                println("SD -- ToolCallResult. node name from context: $nodeNameFromContext")
                println("SD -- ToolCallResult. session id from context: $sessionIdFromContext")

                val toolCallSpanId = SpanEvent.getToolCallId(sessionId = sessionIdFromContext ?: "", nodeId = nodeNameFromContext ?: "")

                spans.get(toolCallSpanId)?.let { span ->
                    span.setAttribute("gen_ai.tool.result", result?.toString() ?: "")
                    span.end()

                    spans.remove(toolCallSpanId)
                    contexts.remove(toolCallSpanId)
                }
            }

            //endregion Tool Call
        }

        //region Private Methods

        private fun endUnfinishedSpans() {
            spans.entries
                .filter { (id, _) -> id != SpanEvent.AGENT.id }
                .forEach { (id, span) ->
                    logger.warn { "Force close span with id: $id" }
                    span.end()
                }
        }

        //endregion Private Methods
    }
}
