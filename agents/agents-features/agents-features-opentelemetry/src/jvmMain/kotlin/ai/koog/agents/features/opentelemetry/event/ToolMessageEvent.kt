package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider

internal class ToolMessageEvent(
    provider: LLMProvider,
    private val toolCallId: String?,
    private val content: String,
    override val verbose: Boolean = false
) : GenAIAgentEvent() {

    override val name: String = super.name.concatEventName("tool.message")

    init {
        // Attributes
        addAttribute(CommonAttributes.System(provider))

        // Body Fields
        if (verbose) {
            addBodyField(EventBodyFields.Content(content = content))
        }

        // Id
        toolCallId?.let { id ->
            addBodyField(EventBodyFields.Id(id = id))
        }

        // Role (conditional).
        // Do not add Role as a tool result guarantee the response has a Tool role.
    }
}
