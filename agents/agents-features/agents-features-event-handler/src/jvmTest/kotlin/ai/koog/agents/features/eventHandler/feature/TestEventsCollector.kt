package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.feature.model.eventString
import ai.koog.agents.core.feature.traceString

class TestEventsCollector {

    val size: Int
        get() = collectedEvents.size

    private val _collectedEvents = mutableListOf<String>()

    val collectedEvents: List<String>
        get() = _collectedEvents.toList()

    val eventHandlerFeatureConfig: EventHandlerConfig.() -> Unit = {

        onBeforeAgentStarted { eventContext ->
            _collectedEvents.add("OnBeforeAgentStarted (agent id: ${eventContext.agent.id}, session id: ${eventContext.sessionId}, strategy: ${eventContext.strategy.name})")
        }

        onAgentFinished { eventContext ->
            _collectedEvents.add("OnAgentFinished (agentId: ${eventContext.agentId}, sessionId: ${eventContext.sessionId}, result: ${eventContext.result})")
        }

        onAgentRunError { eventContext ->
            _collectedEvents.add("OnAgentRunError (agentId: ${eventContext.agentId}, sessionId: ${eventContext.sessionId}, throwable: ${eventContext.throwable.message})")
        }

        onAgentBeforeClose { eventContext ->
            _collectedEvents.add("OnAgentBeforeClose (agentId: ${eventContext.agentId})")
        }

        onStrategyStarted { eventContext ->
            _collectedEvents.add("OnStrategyStarted (strategy: ${eventContext.strategy.name})")
        }

        onStrategyFinished { eventContext ->
            _collectedEvents.add("OnStrategyFinished (strategy: ${eventContext.strategy.name}, result: ${eventContext.result})")
        }

        onBeforeNode { eventContext ->
            _collectedEvents.add("OnBeforeNode (node: ${eventContext.node.name}, input: ${eventContext.input})")
        }

        onAfterNode { eventContext ->
            _collectedEvents.add("OnAfterNode (node: ${eventContext.node.name}, input: ${eventContext.input}, output: ${eventContext.output})")
        }

        onBeforeLLMCall { eventContext ->
            _collectedEvents.add("OnBeforeLLMCall (prompt: ${eventContext.prompt.traceString}, tools: [${eventContext.tools.joinToString { it.name } }])")
        }

        onAfterLLMCall { eventContext ->
            _collectedEvents.add("OnAfterLLMCall (responses: [${eventContext.responses.joinToString { "${it.role.name}: ${it.content}" }}])")
        }

        onStartLLMStream { eventContext ->
            _collectedEvents.add("OnStartLLMStream (session id: ${eventContext.sessionId}, prompt)")
        }

        onBeforeExecuteMultipleChoices { eventContext ->
            _collectedEvents.add("OnBeforeExecuteMultipleChoices (session id: ${eventContext.sessionId}, prompt: ${eventContext.prompt.traceString}, model: ${eventContext.model.eventString}, tools: [${eventContext.tools.joinToString { it.name } }])")
        }

        onAfterExecuteMultipleChoices { eventContext ->
            _collectedEvents.add("OnBeforeExecuteMultipleChoices (session id: ${eventContext.sessionId}, prompt: ${eventContext.prompt.traceString}, model: ${eventContext.model.eventString}, tools: [${eventContext.tools.joinToString { it.name } }], responses: [${eventContext.responses.joinToString { response -> "[${response.joinToString { message -> message.traceString }}]" }}]")
        }

        onToolCall { eventContext ->
            _collectedEvents.add("OnToolCall (tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs})")
        }

        onToolValidationError { eventContext ->
            _collectedEvents.add("OnToolValidationError (tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs}, value: ${eventContext.error})")
        }

        onToolCallFailure { eventContext ->
            _collectedEvents.add("OnToolCallFailure (tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs}, throwable: ${eventContext.throwable.message})")
        }

        onToolCallResult { eventContext ->
            _collectedEvents.add("OnToolCallResult (tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs}, result: ${eventContext.result})")
        }
    }

    fun reset() {
        _collectedEvents.clear()
    }
}
