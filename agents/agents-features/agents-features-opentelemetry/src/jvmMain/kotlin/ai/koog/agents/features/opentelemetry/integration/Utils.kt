package ai.koog.agents.features.opentelemetry.integration

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.event.EventBodyField
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan

internal inline fun <reified TBodyField> GenAIAgentSpan.bodyFieldsToCustomAttribute(
    event: GenAIAgentEvent,
    attributeCreate: (TBodyField) -> Attribute
) where TBodyField : EventBodyField {
    val span = this
    val eventBodyFields = event.bodyFields.filterIsInstance<TBodyField>().toList()

    eventBodyFields.forEach { bodyField ->
        val attributeFromEvent = attributeCreate(bodyField)
        span.addAttribute(attributeFromEvent)
    }

    eventBodyFields.forEach { bodyField -> event.removeBodyField(bodyField) }
}
