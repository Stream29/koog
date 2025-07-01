package ai.koog.agents.features.opentelemetry.feature.attribute

internal object EventAttribute {

    sealed interface System : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("system")

        data class Message(val message: String) : System {
            override val key: String = super.key.concatKey("message")
            override val value: String = message
        }
    }

    sealed interface User : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("user")

        data class Message(val message: String) : System {
            override val key: String = super.key.concatKey("message")
            override val value: String = message
        }
    }


}