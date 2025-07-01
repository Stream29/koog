package ai.koog.agents.features.opentelemetry.attribute

import ai.koog.prompt.llm.LLMProvider

internal object CommonAttributes {

    data class System(private val provider: LLMProvider) : GenAIAttribute {
        override val key: String = super.key.concatKey("system")
        override val value: String = provider.id
    }

    data class ErrorType(private val errorType: String) : Attribute {
        override val key: String = "error.type"
        override val value: String = errorType
    }
}