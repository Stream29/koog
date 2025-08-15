package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal class UserMessageEvent(
    provider: LLMProvider,
    private val message: Message.User,
    override val verbose: Boolean = false
) : GenAIAgentEvent() {

    override val name: String = super.name.concatEventName("user.message")

    init {
        // Attributes
        addAttribute(CommonAttributes.System(provider))

        // Body Fields
        addBodyField(EventBodyFields.Role(role = message.role))

        if (verbose) {
            addBodyField(EventBodyFields.Content(content = message.content))
        }
    }
}
