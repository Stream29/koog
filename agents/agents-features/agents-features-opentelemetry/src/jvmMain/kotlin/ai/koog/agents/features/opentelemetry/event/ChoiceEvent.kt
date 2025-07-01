package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.EventAttribute
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttribute
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal class ChoiceEvent(
    provider: LLMProvider,
    private val message: Message.Response,
    override val verbose: Boolean = false,
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("choice")

    override val attributes: List<GenAIAttribute> = buildList {
        add(GenAIAttribute.System(provider))

        when (message) {
            is Message.Assistant -> {
                add(EventAttribute.Body.Message(
                    role = message.role.takeIf { role -> role == Message.Role.Assistant },
                    content = message.content
                ))

                message.finishReason?.let { reason ->
                    add(EventAttribute.Body.FinishReason(reason))
                }

                add(EventAttribute.Body.Index(0))

            }
            is Message.Tool.Call -> {
                add(EventAttribute.Body.ToolCalls(tools = listOf(message), verbose = verbose))
                add(EventAttribute.Body.Index(0))
            }
        }
    }
}
