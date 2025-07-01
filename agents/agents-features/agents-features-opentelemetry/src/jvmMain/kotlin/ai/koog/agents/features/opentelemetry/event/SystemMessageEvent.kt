package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.EventAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttribute
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal data class SystemMessageEvent(
    private val provider: LLMProvider,
    private val message: Message.System,
    override val verbose: Boolean = false
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("system.message")

    override val attributes: List<GenAIAttribute> = buildList {
        add(CommonAttributes.System(provider))

        add(EventAttributes.Body.Content(content = message.content))

        if (message.role != Message.Role.System) {
            add(EventAttributes.Body.Role(role = message.role))
        }
    }
}
