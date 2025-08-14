package ai.koog.agents.features.opentelemetry.integration.langfuse

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
import ai.koog.agents.features.opentelemetry.span.NodeExecuteSpan
import java.util.concurrent.atomic.AtomicInteger


@OptIn(InternalAgentsApi::class)
internal object LangfuseSpanAdapter : SpanAdapter() {

    private val stepKey = AtomicInteger(0)

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

            is NodeExecuteSpan -> {
                val step = stepKey.getAndIncrement()

                span.addAttribute(
                    CustomAttribute(
                        "langfuse.observation.metadata.langgraph_step",
                        step
                    )
                )

                span.addAttribute(
                    CustomAttribute(
                        "langfuse.observation.metadata.langgraph_node",
                        span.nodeName
                    )
                )
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
                                CustomAttribute("gen_ai.completion.$index.content", bodyField.valueString)
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
