package ai.koog.agents.features.opentelemetry.feature.event

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.features.opentelemetry.feature.attribute.GenAIAttribute
import ai.koog.prompt.llm.LLMProvider

internal class ToolMessageEvent(
    private val provider: LLMProvider,
    private val tool: Tool<*, *>,
    private val toolArgs: ToolArgs,
    override val verbose: Boolean = false
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("tool.message")

    override val attributes: List<GenAIAttribute> = buildList {
        add(GenAIAttribute.System(provider))

    }
}
