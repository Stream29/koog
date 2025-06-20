package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import io.opentelemetry.api.trace.StatusCode
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

        override val key: AIAgentStorageKey<OpenTelemetry> = AIAgentStorageKey("agents-features-opentelemetry")

        override fun createInitialConfig(): OpenTelemetryConfig {
            // TODO: SD -- fix
            return OpenTelemetryConfig()
        }

        override fun install(
            config: OpenTelemetryConfig,
            pipeline: AIAgentPipeline
        ) {
            val interceptContext = InterceptContext(this, OpenTelemetry())

            val tracer = config.sdk.tracerProvider.get("")
            val propagator = config.sdk.propagators

            val rootSpan = tracer.spanBuilder(EventName.AGENT.id).startSpan()

            pipeline.interceptBeforeAgentStarted(interceptContext) {

//                rootSpan.addEvent(EventName.AGENT_BEFORE_START.id)
//                    .setAttribute()

                val span = tracer.spanBuilder(EventName.AGENT_BEFORE_START.id)
                    .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .setAttribute("get_ai.system", agent.agentConfig.model.provider.id)
                    .setAttribute("gen_ai.operation.name", OperationName.INVOKE_AGENT.id)
                    .startSpan()
            }

            pipeline.interceptAgentFinished(interceptContext) { strategyName, result ->

                val span = tracer.spanBuilder(EventName.AGENT_FINISHED.id)
                    .setAttribute("get_ai.system", "") //agent.agentConfig.model.provider.id

                rootSpan.setAttribute("gen_ai.agent.result", result)
                rootSpan.setStatus(StatusCode.OK)
                rootSpan.end()
            }
        }
    }
}
