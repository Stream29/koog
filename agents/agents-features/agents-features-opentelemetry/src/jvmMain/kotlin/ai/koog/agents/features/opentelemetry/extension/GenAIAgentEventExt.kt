package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan

/**
 * Converts the attributes and body fields of a GenAIAgentEvent into span attributes
 * and adds them to the specified {@link GenAIAgentSpan}.
 *
 * @param span The span to which the converted attributes will be added.
 * @param verbose Indicates whether the verbosity option should be applied when converting the body fields.
 */
internal fun GenAIAgentEvent.toSpanAttributes(
    span: GenAIAgentSpan,
    verbose: Boolean = false
) {
    // 1. Convert all event body fields into attributes
    this.bodyFieldsToAttributes(verbose)

    // 2. Convert all event attributes into the span attributes
    attributes.forEach { attribute -> span.addAttribute(attribute) }
}

/**
 * Converts the body fields of the event into attributes and adds them to the event.
 * Each body field is transformed into a custom attribute representation using the
 * `toCustomAttribute` method, and the result is added as an attribute to the event.
 */
internal fun GenAIAgentEvent.bodyFieldsToAttributes(verbose: Boolean = false) {
    // Convert each Body Field into an event attribute
    bodyFields.forEach { bodyField ->
        val attribute = bodyField.toCustomAttribute(verbose)
        this.addAttribute(attribute)
    }

    // Clean all converted body fields
    bodyFields.forEach { bodyField -> removeBodyField(bodyField) }
}

/**
 * Converts the `bodyFields` of the event into a single JSON-like string representation and adds it
 * as a custom "body" attribute to the event's attribute list.
 *
 * If the `bodyFields` list is empty, the method does nothing.
 */
internal fun GenAIAgentEvent.bodyFieldsToBodyAttribute() {
    if (bodyFields.isEmpty()) {
        return
    }

    val value = bodyFields.joinToString(separator = ",", prefix = "{", postfix = "}") { bodyField ->
        "\"${bodyField.key}\":${bodyField.valueString}"
    }

    // Add a new 'body' attribute for the event
    addAttribute(CustomAttribute("body", value, verbose))

    // Clean all converted body fields
    bodyFields.forEach { bodyField -> removeBodyField(bodyField) }
}
