package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.event.AssistantMessageEvent
import ai.koog.agents.features.opentelemetry.event.ChoiceEvent
import ai.koog.agents.features.opentelemetry.event.EventBodyFields
import ai.koog.agents.features.opentelemetry.event.UserMessageEvent
import ai.koog.agents.features.opentelemetry.extension.toSpanAttributes
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.InferenceSpan

@OptIn(InternalAgentsApi::class)
internal object LangfuseSpanAdapter : SpanAdapter {

    override fun processSpan(span: GenAIAgentSpan) {
        if (span is InferenceSpan) {
            span.events.forEach { event ->
                when (event) {
                    is AssistantMessageEvent -> {
                        event.bodyFields.filterIsInstance<EventBodyFields.Role>().firstOrNull()?.let { role ->
                            span.addAttribute(CustomAttribute("gen_ai.completion.${role.key}", role.value))
                        }

                        event.bodyFields.filterIsInstance<EventBodyFields.Content>().firstOrNull()?.let { content ->
                            span.addAttribute(CustomAttribute("gen_ai.completion.content", content.value))
                        }
                    }
                    is ChoiceEvent -> {
                        val index = event.index

                        event.bodyFields.filterIsInstance<EventBodyFields.Role>().firstOrNull()?.let { role ->
                            span.addAttribute(CustomAttribute("gen_ai.completion.$index.${role.key}", role.value))
                        }

                        event.bodyFields.filterIsInstance<EventBodyFields.Content>().firstOrNull()?.let { content ->
                            span.addAttribute(CustomAttribute("gen_ai.completion.$index.content", content.value))
                        }
                    }

                    is UserMessageEvent -> {
                        event.bodyFields.filterIsInstance<EventBodyFields.Content>().firstOrNull()?.let { content ->
                            span.addAttribute(CustomAttribute("gen_ai.prompt", content.value))
                        }
                    }

                    else -> event.toSpanAttributes(span)
                }
            }

            span.clearEvents()
        }
    }
}
