package ai.koog.agents.features.opentelemetry

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.opentelemetry.mock.MockLLMExecutor
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

internal object OpenTelemetryTestAPI {

    internal fun createAgent(
        agentId: String = "test-agent-id",
        strategy: AIAgentStrategy<String, String>,
        promptId: String? = null,
        model: LLModel? = null,
        temperature: Double? = 0.0,
        systemPrompt: String? = null,
        userPrompt: String? = null,
        assistantPrompt: String? = null,
        installFeatures: AIAgent.FeatureContext.() -> Unit = { }
    ): AIAgent<String, String> {

        val clock = testClock

        val agentConfig = AIAgentConfig(
            prompt = prompt(promptId ?: "Test prompt", clock = clock, params = LLMParams(temperature = temperature)) {
                system(systemPrompt ?: "Test system message")
                user(userPrompt ?: "Test user message")
                assistant(assistantPrompt ?: "Test assistant response")
            },
            model = model ?: OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 10,
        )

        return AIAgent(
            id = agentId,
            promptExecutor = MockLLMExecutor(clock),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            },
            clock = clock,
            installFeatures = installFeatures,
        )
    }

    private val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.Companion.parse("2023-01-01T00:00:00Z")
    }
}