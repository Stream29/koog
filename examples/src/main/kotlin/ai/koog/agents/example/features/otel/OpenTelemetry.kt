package ai.koog.agents.example.features

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.example.features.otel.server.OpenTelemetryServer
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {


    // First, start the OpenTelemetry server if not already running
    val serverStarted = OpenTelemetryServer.launch()
    if (!serverStarted) {
        println("Warning: OpenTelemetry server not started. Telemetry data will only be logged to console.")
    }

    try {
        // Create an agent with OpenTelemetry feature
        val agent = createAgentWithTelemetry()

        // Execute the agent
        val result = agent.run("Tell me a joke about programming")
        println("\nAgent result: $result")

        println("\nExecution completed. Check Jaeger UI at http://localhost:16686 to view traces")
    } finally {
        // Stop the server if we started it
        if (serverStarted) {
            println("\nPress ENTER to stop the OpenTelemetry server...")
            readLine()
            OpenTelemetryServer.stop()
        }
    }
}

fun createAgentWithTelemetry(): AIAgent {

    val executor: PromptExecutor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey)

    val toolRegistry = ToolRegistry {}

    val agent = AIAgent(
        executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = "You are a code assistant. Provide concise code examples."
    ) {
        install(OpenTelemetry) {
        }
    }

    return agent
}

