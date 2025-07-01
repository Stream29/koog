package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.EventAttributes
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
            add(EventAttributes.Body.Role(role = message.role))
        }

        when (message) {
            is Message.Assistant -> {
                add(EventAttributes.Body.Content(content = message.content))
            }

            is Message.Tool.Call -> {
                add(EventAttributes.Body.ToolCalls(tools = listOf(message), verbose = verbose))
            }

            is Message.Tool.Result -> {
                add(EventAttributes.Body.ToolCalls(tools = listOf(message), verbose = verbose))
            }

            else -> error(
                "Expected message types: ${Message.Tool.Call::class.simpleName}, " +
                        "${Message.Tool.Result::class.simpleName}, but received: ${message::class.simpleName}"
            )
        }
    }
}
