package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.EventAttribute
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttribute
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal data class AssistantMessageEvent(
    private val provider: LLMProvider,
    private val message: Message,
    override val verbose: Boolean = false
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("assistant.message")

    override val attributes: List<GenAIAttribute> = buildList {
        add(GenAIAttribute.System(provider))

        if (message.role != Message.Role.Assistant) {
            add(EventAttribute.Body.Role(role = message.role))
        }

        when (message) {
            is Message.Assistant -> {
                add(EventAttribute.Body.Content(content = message.content))
            }

            is Message.Tool.Call -> {
                add(EventAttribute.Body.ToolCalls(tools = listOf(message), verbose = verbose))
            }

            is Message.Tool.Result -> {
                add(EventAttribute.Body.ToolCalls(tools = listOf(message), verbose = verbose))
            }

            else -> error(
                "Expected message types: ${Message.Tool.Call::class.simpleName}, " +
                        "${Message.Tool.Result::class.simpleName}, but received: ${message::class.simpleName}"
            )
        }
    }
}
