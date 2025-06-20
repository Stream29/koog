package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
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

            val meter = config.sdk.meterProvider.get("")
            val tracer = config.sdk.tracerProvider.get("")

            pipeline.interceptBeforeAgentStarted(interceptContext) {

                tracer.spanBuilder(EventName.AGENT_BEFORE_START.id)

                meter.gaugeBuilder(EventName.AGENT_BEFORE_START.id)
                    .setDescription("Agent. Before start event.")
                    .setUnit()



//                    .ofLongs() .setUnit("stateOrdinal").buildWithCallback {
//                    it.record(pingTracker.connectionState.value.ordinal.toLong())
//                }
            }
        }
    }
}
