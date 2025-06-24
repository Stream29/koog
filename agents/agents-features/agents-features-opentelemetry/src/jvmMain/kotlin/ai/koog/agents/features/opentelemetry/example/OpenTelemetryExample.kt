package ai.koog.agents.features.opentelemetry.example

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentBuilder
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.model.SystemMessage
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.server.OpenTelemetryServer
import kotlinx.coroutines.runBlocking

/**
 * Example demonstrating how to use OpenTelemetry with AI Agents
 */
public object OpenTelemetryExample {
    @JvmStatic
    public fun main(args: Array<String>) {
        // First, start the OpenTelemetry server if not already running
        val serverStarted = OpenTelemetryServer.launch()
        if (!serverStarted) {
            println("Warning: OpenTelemetry server not started. Telemetry data will only be logged to console.")
        }

        try {
            // Create an agent with OpenTelemetry feature
            runBlocking {
                val agent = createAgentWithTelemetry()

                // Execute the agent
                val result = agent.execute("Tell me a joke about programming")
                println("\nAgent result: $result")
            }

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

    private suspend fun createAgentWithTelemetry(): AIAgent {
        return AIAgentBuilder.create()
            .withConfig(
                AIAgentConfig.Builder()
                    .withSystemMessage(
                        SystemMessage(
                            "You are a helpful assistant that can answer questions about programming."
                        )
                    )
                    .build()
            )
            .withFeature(OpenTelemetry)
            .build()
    }
}
