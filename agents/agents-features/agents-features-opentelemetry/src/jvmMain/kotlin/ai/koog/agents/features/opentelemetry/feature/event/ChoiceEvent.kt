package ai.koog.agents.features.opentelemetry.feature.event

import ai.koog.agents.features.opentelemetry.feature.attribute.EventAttribute
import ai.koog.agents.features.opentelemetry.feature.attribute.GenAIAttribute
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal class ChoiceEvent(
    provider: LLMProvider,
    response: Message.Response,
    override val verbose: Boolean = false,
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("choice")

    override val attributes: List<GenAIAttribute> = buildList {
        add(GenAIAttribute.System(provider))
        add(EventAttribute.Body.Id(provider))
        when (response) {
            is Message.Tool.Call -> {
                add(EventAttribute.Body.ToolCalls(tools = listOf(response), verbose = verbose))
            }

            is Message.Assistant -> {
                add(EventAttribute.Body)
            }
        }
    }
}
