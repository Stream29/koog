package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes

internal class ExceptionEvent(
    private val throwable: Throwable,
    override val verbose: Boolean = false,
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("exception")

    override val attributes: List<Attribute> = buildList {
        add(CommonAttributes.Error.Type(throwable.cause.toString()))
        add(CommonAttributes.Error.Message(throwable.message.toString()))
        add(CommonAttributes.Error.Stacktrace(throwable.stackTraceToString()))
    }

    override val bodyFields: List<EventBodyField> = emptyList()
}
