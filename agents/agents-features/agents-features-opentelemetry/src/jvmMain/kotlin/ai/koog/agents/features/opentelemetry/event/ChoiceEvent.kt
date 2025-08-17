package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.JsonObject

internal class ChoiceEvent(
    provider: LLMProvider,
    private val message: Message.Response,
    private val arguments: JsonObject? = null,
    val index: Int,
    override val verbose: Boolean = false,
) : GenAIAgentEvent() {

    override val name: String = super.name.concatEventName("choice")

    init {
        // Attributes
        addAttribute(CommonAttributes.System(provider))

        // Body Fields
        addBodyField(EventBodyFields.Role(role = message.role))
        addBodyField(EventBodyFields.Index(index = index))

        when (message) {
            is Message.Assistant -> {
                message.finishReason?.let { reason ->
                    addBodyField(EventBodyFields.FinishReason(reason))
                }

                if (verbose) {
                    addBodyField(
                        EventBodyFields.Message(
                            role = message.role,
                            content = message.content
                        )
                    )

                    arguments?.let { addBodyField(EventBodyFields.Arguments(arguments = it)) }
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
