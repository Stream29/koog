package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute

internal interface GenAIAgentEvent {

    val name: String
        get() = "gen_ai"

    val attributes: List<Attribute>

    /**
     * The body field for the event.
     *
     * Note: Currently, the OpenTelemetry SDK does not support event body fields.
     *       This field is used to store the body fields.
     *       Fields are merged with attributes when creating the event.
     */
    val bodyFields: List<EventBodyField>

    val nonSensitiveBodyFields: List<EventBodyField>
        get() = bodyFields.filterNot { field -> field.sensitive }

    fun String.concatName(other: String): String = "$this.$other"

    fun bodyFieldsAsAttribute(verbose: Boolean): Attribute {
        check(bodyFields.isNotEmpty()) {
            "Unable to convert Event Body Fields into Attribute because no body fields found"
        }

        val bodyFieldsToProcess = if (verbose) bodyFields else nonSensitiveBodyFields

        val value = bodyFieldsToProcess.joinToString(separator = ",", prefix = "{", postfix = "}") { bodyField ->
            "\"${bodyField.key}\":${bodyField.valueString}"
        }

        return CustomAttribute("body", value)
    }
}
