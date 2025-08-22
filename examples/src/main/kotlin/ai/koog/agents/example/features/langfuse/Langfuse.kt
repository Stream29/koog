package ai.koog.agents.example.features.langfuse

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
import ai.koog.agents.features.opentelemetry.integration.weave.addWeaveExporter
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import kotlinx.coroutines.runBlocking

/**
 * Example of Koog agents tracing to [Langfuse](https://langfuse.com/)
 *
 * Agent traces are exported to:
 * - Langfuse OTLP endpoint instance using [OtlpHttpSpanExporter]
 *
 * To run this example:
 *  1. Set up a Langfuse project and credentials as described [here](https://langfuse.com/docs/get-started#create-new-project-in-langfuse)
 *  2. Get Langfuse credentials as described [here](https://langfuse.com/faq/all/where-are-langfuse-api-keys)
 *  3. Set `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, and `LANGFUSE_SECRET_KEY` environment variables
 *
 * @see <a href="https://langfuse.com/docs/opentelemetry/get-started#opentelemetry-endpoint">Langfuse OpenTelemetry Docs</a>
 */
fun main() = runBlocking {
    val agent = AIAgent(
        executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        llmModel = OpenAIModels.CostOptimized.GPT4oMini,
        systemPrompt = "You are a code assistant. Provide concise code examples."
    ) {
        install(OpenTelemetry) {
            addLangfuseExporter()

            addWeaveExporter(
                weaveOtelBaseUrl = "WEAVE_TELEMETRY_URL",
                weaveApiKey = "WEAVE_API_KEY",
                weaveEntity = "WEAVE_ENTITY",
                weaveProjectName = "WEAVE_PROJECT_NAME"
            )
        }
    }

    println("Running agent with Langfuse tracing")

    val result = agent.run("Tell me a joke about programming")

    println("Result: $result\nSee traces on the Langfuse instance")
}
