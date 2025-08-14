package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.event.AssistantMessageEvent
import ai.koog.agents.features.opentelemetry.event.ChoiceEvent
import ai.koog.agents.features.opentelemetry.event.EventBodyFields
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.integration.bodyFieldsToCustomAttribute
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.InferenceSpan

@OptIn(InternalAgentsApi::class)
internal object LangfuseSpanAdapter : SpanAdapter() {

    override fun onBeforeSpanStarted(span: GenAIAgentSpan) {
        when (span) {
            is InferenceSpan -> {
                // Each event - convert into the span attribute
                span.events.forEachIndexed { index, event ->

                    when (event) {
                        is AssistantMessageEvent -> {

                            // Convert event data fields into the span attributes
                            span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(event) { bodyField ->
                                CustomAttribute("gen_ai.prompt.$index.${bodyField.key}", bodyField.value)
                            }

                            span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(event) { bodyField ->
                                CustomAttribute("gen_ai.prompt.$index.${bodyField.key}", bodyField.value)
                            }

                            // Delete event from the span
                            span.removeEvent(event)
                        }

                        // TODO: ?
                    }
                }
            }
        }
    }

    override fun onBeforeSpanFinished(span: GenAIAgentSpan) {
        when (span) {
            is InferenceSpan -> {
                span.events.forEach { event ->
                    when (event) {
                        is ChoiceEvent -> {

                            // Convert event data fields into the span attributes
                            val index = event.index

                            span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(event) { bodyField ->
                                CustomAttribute("gen_ai.completion.$index.${bodyField.key}", bodyField.value)
                            }

                            span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(event) { bodyField ->
                                CustomAttribute("gen_ai.completion.$index.${bodyField.key}", bodyField.value)
                            }

                            // Delete event from the span
                            span.removeEvent(event)
                        }
                    }
                }
            }
        }
    }
}
