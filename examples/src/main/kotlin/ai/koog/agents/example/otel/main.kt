package ai.koog.agents.example.otel

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import java.util.concurrent.TimeUnit

/**
 * Before running the example, start Jaeger all in one container.
 * docker-compose.yaml
 *   services:
 *   jaeger-all-in-one:
 *     image: jaegertracing/all-in-one:1.39
 *     container_name: jaeger-all-in-one
 *     environment:
 *       - COLLECTOR_OTLP_ENABLED=true
 *     ports:
 *       - "4317:4317"
 *       - "16686:16686"
 *
 */
suspend fun main() {
    val apiKey = "" // GOTO: https://aistudio.google.com/
    val agent = AIAgent(
        executor = simpleGoogleAIExecutor(apiKey),
        llmModel = GoogleModels.Gemini2_0Flash,
        systemPrompt = "You are a code assistant. Provide concise code examples.",
        installFeatures = {
            install(OpenTelemetry) {
                serviceName = "my-beautiful-agent"
                addOtlpExporter()
            }
        }
    )

    val result = agent.runAndGetResult("Create python function that prints hello world")
    println(result)
    TimeUnit.SECONDS.sleep(10)
}