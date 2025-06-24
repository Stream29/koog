package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.features.opentelemetry.feature.interceptor.OpenTelemetryAiAgentPipelineInterceptor
import io.opentelemetry.api.GlobalOpenTelemetry
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

        override val key: AIAgentStorageKey<OpenTelemetry> = AIAgentStorageKey("agents-features-opentelemetry")

        override fun createInitialConfig(): OpenTelemetryConfig {
            return OpenTelemetryConfig()
        }

        override fun install(
            config: OpenTelemetryConfig,
            pipeline: AIAgentPipeline
        ) {
            val interceptContext = InterceptContext(this, OpenTelemetry())
            OpenTelemetryAiAgentPipelineInterceptor(
                interceptContext,
                config.buildSdk().getTracer("koog-agent")
            ).interceptCalls(pipeline)
        }
    }
}
