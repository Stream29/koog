package ai.koog.agents.example.nongraph


import ai.koog.agents.core.agent.agentImpls.AIAgent
import ai.koog.agents.core.dsl.builder.simpleStrategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.example.calculator.CalculatorTools
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val executor: PromptExecutor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey)

    // Create a tool registry with calculator tools
    val toolRegistry = ToolRegistry {
        // Special tool, required with this type of agent.
        tool(AskUser)
        tool(SayToUser)
        tools(CalculatorTools().asTools())
    }

    // Create the agent
    val agent = AIAgent(
        executor = executor,
        llmModel = OpenAIModels.Chat.GPT4o,
        strategy = simpleStrategy("calculator") { input ->
            val responses = requestLLMMultiple(input)

            while (responses.containsToolCalls()) {
                val tools = extractToolCalls(responses)

                if (latestTokenUsage(tools) > 100500) {
                    compressHistory()
                }

                val results = executeMultipleTools(tools)
                sendMultipleToolResults(results)

            }

            responses.single().asAssistantMessage().content
        },
        systemPrompt = "You are a calculator.",
        toolRegistry = toolRegistry
    ) {
        handleEvents {
            onToolCall { eventContext ->
                println("Tool called: tool ${eventContext.tool.name}, args ${eventContext.toolArgs}")
            }

            onAgentRunError { eventContext ->
                println("An error occurred: ${eventContext.throwable.message}\n${eventContext.throwable.stackTraceToString()}")
            }

            onAgentFinished { eventContext ->
                println("Result: ${eventContext.result}")
            }
        }
    }

    runBlocking {
        agent.run("(10 + 20) * (5 + 5) / (2 - 11)")
    }
}
