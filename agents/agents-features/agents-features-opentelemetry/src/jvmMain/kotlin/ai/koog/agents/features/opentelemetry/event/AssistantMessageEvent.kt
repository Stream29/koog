package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.JsonObject

internal class AssistantMessageEvent(
    provider: LLMProvider,
    val message: Message.Response,
    val arguments: JsonObject? = null,
    override val verbose: Boolean = false
) : GenAIAgentEvent() {

    override val name: String = super.name.concatEventName("assistant.message")

    init {
        // Attributes
        addAttribute(CommonAttributes.System(provider))

        // Body Fields
        if (message.role != Message.Role.Assistant) {
            addBodyField(EventBodyFields.Role(role = message.role))
        }

        when (message) {
            is Message.Assistant -> {
                if (verbose) {
                    addBodyField(EventBodyFields.Content(content = message.content))
                    arguments?.let { addBodyField(EventBodyFields.Arguments(it)) }
                }
            }

            is Message.Tool.Call -> {
                if (verbose) {
                    addBodyField(EventBodyFields.ToolCalls(tools = listOf(message)))
                }
            }
        }
    }
}
