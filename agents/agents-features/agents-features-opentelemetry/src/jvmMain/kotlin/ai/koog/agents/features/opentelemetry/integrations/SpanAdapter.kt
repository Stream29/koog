package ai.koog.agents.features.opentelemetry.integrations

import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan

/**
 * Adapter interface for post-processing GenAI agent spans.
 *
 * This interface allows customization of how GenAI agent spans are processed after they are created.
 * Implementations can modify span data, add additional attributes or events, or perform any other
 * post-processing logic needed before the span is completed.
 *
 * The interface provides a single method called after a span is created but before it is finished.
 */
internal interface SpanAdapter {

    /**
     * Post-processes a GenAI agent span after creation but before completion.
     *
     * This method allows customizing how agent spans are processed by modifying span data,
     * adding attributes/events, or performing other processing logic needed before the span is finished.
     *
     * @param span The GenAI agent span to process
     */
    fun processSpan(span: GenAIAgentSpan)
}
