package ai.koog.agents.example.features.otel

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Example of using OpenTelemetry with Koog agents
 * 
 * This example automatically:
 * 1. Starts the OpenTelemetry services using docker-compose
 * 2. Runs the agent with OpenTelemetry tracing
 * 3. Stops the OpenTelemetry services
 * 
 * After running, you can view traces in the Jaeger UI at: http://localhost:16686
 */
fun main() {
    // Directory containing the docker-compose.yaml file
    val dockerComposeDir = "examples/src/main/kotlin/ai/koog/agents/example/features/otel"
    
    try {
        // Start OpenTelemetry services
        println("Starting OpenTelemetry services...")
        executeCommand("cd $dockerComposeDir && docker-compose up -d")
        
        // Wait for services to be ready
        println("Waiting for services to be ready...")
        TimeUnit.SECONDS.sleep(5)
        
        // Run the agent
        runAgent()
        
        // Give some time to view the traces
        println("Services will be stopped in 10 seconds. Visit http://localhost:16686 to view traces.")
        TimeUnit.SECONDS.sleep(10)
    } finally {
        // Stop OpenTelemetry services
        println("Stopping OpenTelemetry services...")
        executeCommand("cd $dockerComposeDir && docker-compose down")
    }
}

/**
 * Executes a shell command
 */
private fun executeCommand(command: String): Int {
    val process = Runtime.getRuntime().exec(arrayOf("/bin/sh", "-c", command))
    process.inputStream.bufferedReader().lines().forEach(::println)
    process.errorStream.bufferedReader().lines().forEach(::println)
    return process.waitFor()
}

/**
 * Runs the agent with OpenTelemetry tracing
 */
fun runAgent() = runBlocking {
    // Create an agent with OpenTelemetry feature
    val agent = AIAgent(
        executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = "You are a code assistant. Provide concise code examples."
    ) {
        install(OpenTelemetry) {
            // Add a console logger for local debugging
            addSpanExporter(LoggingSpanExporter.create())
            
            // Send traces to OpenTelemetry collector
            addSpanExporter(
                OtlpGrpcSpanExporter.builder()
                    .setEndpoint("http://localhost:4317")
                    .build()
            )
        }
    }

    // Execute the agent a couple of times to generate some traces
    println("Running agent with OpenTelemetry tracing...")
    val result = agent.run("Tell me a joke about programming")
    agent.run("Tell me another joke about programming")
    
    println("\nAgent result: $result")
    println("\nExecution completed. Check Jaeger UI at http://localhost:16686 to view traces")
}

