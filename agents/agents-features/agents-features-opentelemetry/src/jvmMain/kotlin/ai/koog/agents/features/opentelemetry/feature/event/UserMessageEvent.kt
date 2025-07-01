package ai.koog.agents.features.opentelemetry.feature.event

import ai.koog.agents.features.opentelemetry.feature.attribute.EventAttribute
import ai.koog.agents.features.opentelemetry.feature.attribute.GenAIAttribute
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal data class UserMessageEvent(
    private val provider: LLMProvider,
    private val prompt: Prompt,
    override val verbose: Boolean = false
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("user.message")

    override val attributes: List<GenAIAttribute> = buildList {
        add(GenAIAttribute.System(provider))
        prompt.messages.findLast { it.role == Message.Role.User }?.let { message ->
            add(EventAttribute.Body.Role(role = message.role))
            add(EventAttribute.Body.Content(content = message.content))
        }
    }
}
