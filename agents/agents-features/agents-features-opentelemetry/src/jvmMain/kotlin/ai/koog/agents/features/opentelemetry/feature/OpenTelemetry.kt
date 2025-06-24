package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
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

        private val contextMap = ConcurrentHashMap<Long, Context>()
        private val contextStack = ConcurrentLinkedDeque<Context>()
        private val spans = ConcurrentHashMap<String, Span>()


        override val key: AIAgentStorageKey<OpenTelemetry> = AIAgentStorageKey("agents-features-opentelemetry")

        override fun createInitialConfig(): OpenTelemetryConfig {
            // TODO: SD -- fix
            return OpenTelemetryConfig()
        }

        override fun install(
            config: OpenTelemetryConfig,
            pipeline: AIAgentPipeline
        ) {

            // Setup tracer
            val tracer = OpenTelemetryStarter.create().getTracer("koog")
            val propagator = config.sdk.propagators

            val rootSpan = tracer.spanBuilder(EventName.AGENT.id).startSpan()
            val scope = rootSpan.makeCurrent()

            // Setup telemetry context
            val parentContext = Context.root()
            contextStack.push(parentContext)

            // Setup telemetry spans
            spans.put("root", rootSpan)

            // Setup feature
            val interceptContext = InterceptContext(this, OpenTelemetry())

            pipeline.interceptBeforeAgentStarted(interceptContext) {

//                rootSpan.addEvent(EventName.AGENT_BEFORE_START.id)
//                    .setAttribute()

                val parentContext = contextStack.peek()
                val context = Context.current()
                contextStack.push(context)

                val span = tracer.spanBuilder(EventName.AGENT_BEFORE_START.id)
                    .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .setParent(parentContext)
                    .setAttribute("get_ai.system", agent.agentConfig.model.provider.id)
                    .setAttribute("gen_ai.operation.name", OperationName.INVOKE_AGENT.id)
                    .startSpan()

                spans.put(EventName.AGENT_BEFORE_START.id, span)
            }

            pipeline.interceptAgentFinished(interceptContext) {
                agentId: String,
                sessionId: String,
                strategyName: String,
                result: String? ->

                spans.get(EventName.AGENT_BEFORE_START.id)?.let { span ->
                    span.end()
                    spans.remove(EventName.AGENT_BEFORE_START.id)

                    rootSpan.setAttribute("gen_ai.agent.result", result)
                    rootSpan.setStatus(StatusCode.OK)

                    scope.close()
                    rootSpan.end()
                }
            }

            pipeline.interceptBeforeNode(interceptContext) { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any? ->
                val span = tracer.spanBuilder(EventName.NODE_EXECUTION_START.id)
                    .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .setParent(parentContext)
                    .setAttribute("get_ai.system", context.sessionId)
                    .setAttribute("gen_ai.operation.name", OperationName.EXECUTE_NODE.id)
                    .startSpan()

                spans.put("${EventName.NODE_EXECUTION_START.id}-${node.name}", span)
            }

            pipeline.interceptAfterNode(interceptContext) { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any? ->
                spans.get("${EventName.NODE_EXECUTION_START.id}-${node.name}")?.let { span ->
                    span.end()
                    spans.remove(EventName.NODE_EXECUTION_START.id)
                }
            }
        }
    }
}
