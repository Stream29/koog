package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.EventAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal class ToolMessageEvent(
    private val provider: LLMProvider,
    private val message: Message.Tool.Result,
    override val verbose: Boolean = false
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("tool.message")

    override val attributes: List<Attribute> = buildList {
        add(CommonAttributes.System(provider))

        // Content
        add(EventAttributes.Body.Content(content = message.content))

        // Id
        message.id?.let { id ->
            add(EventAttributes.Body.Id(id = id))
        }

        // Role (conditional)
        if (message.role != Message.Role.Tool) {
            add(EventAttributes.Body.Role(role = message.role))
        }

    }
}
