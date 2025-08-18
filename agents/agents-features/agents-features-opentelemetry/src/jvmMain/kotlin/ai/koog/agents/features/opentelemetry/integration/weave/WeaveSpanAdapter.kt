package ai.koog.agents.features.opentelemetry.integration.weave

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.event.AssistantMessageEvent
import ai.koog.agents.features.opentelemetry.event.ChoiceEvent
import ai.koog.agents.features.opentelemetry.event.EventBodyFields
import ai.koog.agents.features.opentelemetry.event.SystemMessageEvent
import ai.koog.agents.features.opentelemetry.event.ToolMessageEvent
import ai.koog.agents.features.opentelemetry.event.UserMessageEvent
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.integration.bodyFieldsToCustomAttribute
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.InferenceSpan

/**
 * WeaveSpanAdapter is a specialized implementation of [SpanAdapter] designed to process and transform
 * spans related to Generative AI agent events. It provides customized handling for specific types of
 * events, converting their data fields into span attributes and removing processed events.
 *
 * This adapter specifically handles spans of type [InferenceSpan] and processes events such as
 * [SystemMessageEvent], [UserMessageEvent], [AssistantMessageEvent], and [ToolMessageEvent].
 * Events are converted into custom span attributes for better traceability and observability.
 *
 * @param verbose A flag to control the verbosity of the processing. When set to true, additional
 * details may be added to span attributes during processing.
 */
@OptIn(InternalAgentsApi::class)
internal class WeaveSpanAdapter(private val verbose: Boolean) : SpanAdapter() {

    override fun onBeforeSpanStarted(span: GenAIAgentSpan) {
        when (span) {
            is InferenceSpan -> {
                val eventsToProcess = span.events.toList()

                // Each event - convert into the span attribute
                eventsToProcess.forEachIndexed { index, event ->
                    when (event) {
                        is SystemMessageEvent,
                        is UserMessageEvent,
                        is AssistantMessageEvent,
                        is ToolMessageEvent -> {
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
                    }
                }
            }
        }
    }

    override fun onBeforeSpanFinished(span: GenAIAgentSpan) {
        when (span) {
            is InferenceSpan -> {
                val eventsToProcess = span.events.toList()

                eventsToProcess.forEachIndexed { index, event ->
                    when (event) {
                        is AssistantMessageEvent -> {
                            // Convert event data fields into the span attributes
                            span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(event) { bodyField ->
                                CustomAttribute("gen_ai.completion.$index.${bodyField.key}", bodyField.value)
                            }

                            span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(event) { bodyField ->
                                CustomAttribute("gen_ai.completion.$index.${bodyField.key}", bodyField.value)
                            }

                            // Delete event from the span
                            span.removeEvent(event)
                        }

                        is ChoiceEvent -> {
                            span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(event) { bodyField ->
                                CustomAttribute("gen_ai.completion.$index.${bodyField.key}", bodyField.value)
                            }

                            // For Assistant message event
                            span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(event) { bodyField ->
                                CustomAttribute("gen_ai.completion.$index.${bodyField.key}", bodyField.value)
                            }

                            // For Tool call message event
                            span.bodyFieldsToCustomAttribute<EventBodyFields.ToolCalls>(event) { bodyField ->
                                CustomAttribute("gen_ai.completion.$index.content", bodyField.valueString(verbose))
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
