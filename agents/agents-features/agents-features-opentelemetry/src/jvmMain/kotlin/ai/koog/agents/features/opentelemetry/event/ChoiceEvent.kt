package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.JsonObject

internal class ChoiceEvent(
    provider: LLMProvider,
    private val message: Message.Response,
    private val arguments: JsonObject? = null,
    val index: Int,
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("choice")

    override val attributes: List<Attribute> = buildList {
        add(CommonAttributes.System(provider))
    }

    override val bodyFields: List<EventBodyField> = buildList {
        add(EventBodyFields.Index(index))

        when (message) {
            is Message.Assistant -> {
                message.finishReason?.let { reason -> add(EventBodyFields.FinishReason(reason)) }
                add(EventBodyFields.Message(role = message.role, content = message.content))
                arguments?.let { add(EventBodyFields.Arguments(it)) }
            }

            is Message.Tool.Call -> {
                add(EventBodyFields.ToolCalls(tools = listOf(message)))
            }
        }
    }
}
