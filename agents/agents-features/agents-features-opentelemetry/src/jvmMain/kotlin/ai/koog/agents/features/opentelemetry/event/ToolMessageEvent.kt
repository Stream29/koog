package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.EventAttributes
import ai.koog.prompt.llm.LLMProvider

internal class ToolMessageEvent(
    private val provider: LLMProvider,
    private val toolCallId: String?,
    private val toolResult: ToolResult,
    override val verbose: Boolean = false
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("tool.message")

    override val attributes: List<Attribute> = buildList {
        add(CommonAttributes.System(provider))

        // Content
        add(EventAttributes.Body.Content(content = toolResult.toStringDefault()))

        // Id
        toolCallId?.let { id ->
            add(EventAttributes.Body.Id(id = id))
        }

        // Role (conditional).
        // Do not add Role as a tool result guarantee the response has a Tool role.
    }
}
