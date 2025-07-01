package ai.koog.agents.features.opentelemetry.feature.event

import ai.koog.agents.features.opentelemetry.feature.attribute.EventAttribute
import ai.koog.agents.features.opentelemetry.feature.attribute.GenAIAttribute
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal class ToolMessageEvent(
    private val provider: LLMProvider,
    private val message: Message.Tool.Result,
    override val verbose: Boolean = false
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("tool.message")

    override val attributes: List<GenAIAttribute> = buildList {
        add(GenAIAttribute.System(provider))

        // Content
        add(EventAttribute.Body.Content(content = message.content))

        // Id
        message.id?.let { id ->
            add(EventAttribute.Body.Id(id = id))
        }

        // Role (conditional)
        if (message.role != Message.Role.Tool) {
            add(EventAttribute.Body.Role(role = message.role))
        }

    }
}
