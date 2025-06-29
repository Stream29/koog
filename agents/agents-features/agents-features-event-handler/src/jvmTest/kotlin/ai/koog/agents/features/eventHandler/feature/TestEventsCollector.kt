package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

class TestEventsCollector {

    val size: Int
        get() = collectedEvents.size

    private val _collectedEvents = mutableListOf<String>()

    val collectedEvents: List<String>
        get() = _collectedEvents.toList()

    val eventHandlerFeatureConfig: EventHandlerConfig.() -> Unit = {

        onBeforeAgentStarted { strategy: AIAgentStrategy<*, *>, agent: AIAgent<*, *> ->
            _collectedEvents.add("OnBeforeAgentStarted (agent id: ${agent.id}, strategy: ${strategy.name})")
        }

        onAgentFinished { agentId: String, sessionId: String, strategyName: String, result: Any? ->
            _collectedEvents.add("OnAgentFinished (agentId: $agentId, strategy: $strategyName, result: $result)")
        }

        onAgentRunError { agentId: String, sessionId: String, strategyName: String, throwable: Throwable ->
            _collectedEvents.add("OnAgentRunError (agentId: $agentId, strategy: $strategyName, throwable: ${throwable.message})")
        }

        onAgentBeforeClose { agentId: String ->
            _collectedEvents.add("OnAgentBeforeClose (agentId: $agentId)")
        }

        onStrategyStarted { strategy: AIAgentStrategy<*, *> ->
            _collectedEvents.add("OnStrategyStarted (strategy: ${strategy.name})")
        }

        onStrategyFinished { strategy: AIAgentStrategy<*, *>, result: Any? ->
            _collectedEvents.add("OnStrategyFinished (strategy: ${strategy.name}, result: $result)")
        }

        onBeforeNode { context: AIAgentContextBase, node: AIAgentNodeBase<*, *>, input: Any? ->
            _collectedEvents.add("OnBeforeNode (node: ${node.name}, input: $input)")
        }

        onAfterNode { context: AIAgentContextBase, node: AIAgentNodeBase<*, *>, input: Any?, output: Any? ->
            _collectedEvents.add("OnAfterNode (node: ${node.name}, input: $input, output: $output)")
        }

        onBeforeLLMCall { prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel ->
            _collectedEvents.add("OnBeforeLLMCall (prompt: ${prompt.messages}, tools: [${tools.joinToString { it.name } }])")
        }

        onAfterLLMCall { prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, responses: List<Message.Response> ->
            _collectedEvents.add("OnAfterLLMCall (responses: [${responses.joinToString { "${it.role.name}: ${it.content}" }}])")
        }

        onToolCall { tool: Tool<*, *>, toolArgs: ToolArgs ->
            _collectedEvents.add("OnToolCall (tool: ${tool.name}, args: $toolArgs)")
        }

        onToolValidationError { tool: Tool<*, *>, toolArgs: ToolArgs, value: String ->
            _collectedEvents.add("OnToolValidationError (tool: ${tool.name}, args: $toolArgs, value: $value)")
        }

        onToolCallFailure { tool: Tool<*, *>, toolArgs: ToolArgs, throwable: Throwable ->
            _collectedEvents.add("OnToolCallFailure (tool: ${tool.name}, args: $toolArgs, throwable: ${throwable.message})")
        }

        onToolCallResult { tool: Tool<*, *>, toolArgs: ToolArgs, result: ToolResult? ->
            _collectedEvents.add("OnToolCallResult (tool: ${tool.name}, args: $toolArgs, result: $result)")
        }
    }

    fun reset() {
        _collectedEvents.clear()
    }
}
