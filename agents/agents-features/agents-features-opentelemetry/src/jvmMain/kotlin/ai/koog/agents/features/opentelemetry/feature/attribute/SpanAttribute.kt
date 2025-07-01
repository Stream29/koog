package ai.koog.agents.features.opentelemetry.feature.attribute

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.features.opentelemetry.feature.span.spanElement
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

internal object SpanAttribute {

    data class Custom(private val keySuffix: String, override val value: Any) : GenAIAttribute {
        override val key: String
            get() {
                if (keySuffix.startsWith(super.key)) {
                    return key
                }
                return super.key.concatKey(keySuffix)
            }
    }

    sealed interface Operation : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("operation")

        data class Name(private val operation: OperationName) : Operation {
            override val key: String = super.key.concatKey("name")
            override val value: String = operation.id
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
        override val key: String
            get() = super.key.concatKey("agent")

        data class Description(private val description: String) : Agent {
            override val key: String = super.key.concatKey("description")
            override val value: String = description
        }

        data class Id(private val id: String) : Agent {
            override val key: String = super.key.concatKey("id")
            override val value: String = id
        }

        data class Name(private val name: String) : Agent {
            override val key: String = super.key.concatKey("name")
            override val value: String = name
        }
    }

    sealed interface Conversation : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("conversation")

        data class Id(private val id: String) : Conversation {
            override val key: String = super.key.concatKey("id")
            override val value: String = id
        }
    }

    sealed interface Output : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("output")

        data class Type(private val type: OutputType) : Output {
            override val key: String = super.key.concatKey("type")
            override val value: String = type.id
        }

        enum class OutputType(val id: String) {
            TEXT("text"),
            JSON("json"),
            IMAGE("image"),
        }
    }

    sealed interface Request : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("request")

        data class Model(private val model: LLModel) : Request {
            override val key: String = super.key.concatKey("model")
            override val value: String = model.id
        }

        data class Temperature(private val temperature: Double) : Request {
            override val key: String = super.key.concatKey("temperature")
            override val value: Double = temperature
        }

        data class Tools(private val tools: List<ToolDescriptor>) : Request {
            override val key: String = super.key.concatKey("tools")
            override val value: List<String> = tools.map { tool -> tool.spanElement }
        }
    }

    sealed interface Response : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("response")

        data class Id(private val id: String) : Response {
            override val key: String = super.key.concatKey("id")
            override val value: String = id
        }

        data class Model(private val model: LLModel) : Response {
            override val key: String = super.key.concatKey("model")
            override val value: String = model.id
        }

        data class FinishReasons(private val reasons: List<String>) : Response {
            override val key: String = super.key.concatKey("finish_reasons")
            override val value: List<String> = reasons
        }
    }

    data class System(private val provider: LLMProvider) : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("system")

        override val value: String = provider.id
    }

    sealed interface Tool : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("tool")

        data class Name(private val name: String) : Tool {
            override val key: String = super.key.concatKey("name")
            override val value: String = name
        }

        data class Description(private val description: String) : Tool {
            override val key: String = super.key.concatKey("description")
            override val value: String = description
        }

        sealed interface Call : Tool {
            override val key: String
                get() = super.key.concatKey("call")

            data class Id(private val id: String) : Call {
                override val key: String = super.key.concatKey("id")
                override val value: String = id
            }
        }
    }
}