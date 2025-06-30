package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

internal sealed interface GenAIAttribute {
    val key: String
    val value: Any

    data class Custom(override val key: String, override val value: Any) : GenAIAttribute

    sealed interface Operation : GenAIAttribute {

        data class Name(private val operation: String) : Operation {
            override val key: String = "gen_ai.operation.name"
            override val value: String = operation
        }

        enum class OperationName(val id: String) {
            CHAT("chat"),
            CREATE_AGENT("create_agent"),
            EMBEDDINGS("embeddings"),
            EXECUTE_TOOL("execute_tool"),
            GENERATE_CONTENT("generate_content"),
            INVOKE_AGENT("invoke_agent"),
            TEXT_COMPLETION("text_completion"),

            RUN_STRATEGY("run_strategy"),
            EXECUTE_NODE("execute_node"),
        }
    }

    sealed interface Agent : GenAIAttribute {

        data class Description(private val description: String) : Agent {
            override val key: String = "gen_ai.agent.description"
            override val value: String = description
        }

        data class Id(private val id: String) : Agent {
            override val key: String = "gen_ai.agent.id"
            override val value: String = id
        }

        data class Name(private val name: String) : Agent {
            override val key: String = "gen_ai.agent.name"
            override val value: String = name
        }
    }

    sealed interface Output : GenAIAttribute {

        data class Type(private val type: OutputType) : Output {
            override val key: String = "gen_ai.output.type"
            override val value: String = type.id
        }

        enum class OutputType(val id: String) {
            TEXT("text"),
            JSON("json"),
            IMAGE("image"),
        }
    }

    sealed interface Request : GenAIAttribute {

        data class Model(private val model: LLModel) : Request {
            override val key: String = "gen_ai.request.model"
            override val value: String = model.id
        }

        data class Temperature(private val temperature: Double) : Request {
            override val key: String = "gen_ai.request.temperature"
            override val value: Double = temperature
        }

        data class Tools(private val tools: List<ToolDescriptor>) : Request {
            override val key: String = "gen_ai.request.tools"
            override val value: List<String> = tools.map { tool -> tool.spanElement }
        }
    }

    sealed interface Response : GenAIAttribute {

        data class Id(private val id: String) : Response {
            override val key: String = "gen_ai.response.id"
            override val value: String = id
        }

        data class Model(private val model: LLModel) : Response {
            override val key: String = "gen_ai.response.model"
            override val value: String = model.id
        }

        data class FinishReasons(private val reasons: List<String>) : Response {
            override val key: String = "gen_ai.response.finish_reasons"
            override val value: List<String> = reasons
        }
    }

    data class System(private val provider: LLMProvider) : GenAIAttribute {
        override val key: String = "gen_ai.system"
        override val value: String = provider.id
    }

    sealed interface Tool : GenAIAttribute {

        data class Name(private val name: String) : Tool {
            override val key: String = "gen_ai.tool.name"
            override val value: String = name
        }

        data class Description(private val description: String) : Tool {
            override val key: String = "gen_ai.tool.description"
            override val value: String = description
        }
    }
}
