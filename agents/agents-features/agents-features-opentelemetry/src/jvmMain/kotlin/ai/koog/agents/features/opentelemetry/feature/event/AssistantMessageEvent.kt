package ai.koog.agents.features.opentelemetry.feature.event

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.features.opentelemetry.feature.attribute.EventAttribute
import ai.koog.agents.features.opentelemetry.feature.attribute.GenAIAttribute
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal data class AssistantMessageEvent(
    private val provider: LLMProvider,
    private val prompt: Prompt,
    override val verbose: Boolean = false
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("assistant.message")

    override val attributes: List<GenAIAttribute> = buildList {
        add(GenAIAttribute.System(provider))

        val lastMessage = prompt.messages.lastOrNull()

        prompt.messages.findLast { it.role == Message.Role.Assistant }?.let { message ->
            add(EventAttribute.Body.Role(role = message.role))
            add(EventAttribute.Body.Content(content = message.content))

            if (lastMessage != null && lastMessage.role == Message.Role.Tool) {
                when (lastMessage) {
                    is Message.Tool.Call -> {
                        lastMessage.
                    }
                    is Message.Tool.Result -> {
                        lastMessage.
                    }
                    else -> {}
                }
            }
//            tools.isNotEmpty()) {
//                add(EventAttribute.Body.ToolCalls())
//            }
        }
    }
}
