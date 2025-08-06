package ai.koog.agents.features.opentelemetry.attribute

internal object SpanAttributesFromEvents {

    // prompt
    sealed interface Prompt : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("prompt")
    }

    data class PromptIndexedRole(
        private val index: Int,
        private val message: ai.koog.prompt.message.Message.Request,
    ) : Prompt {
        override val key: String = super.key.concatKey("${index}.role")
        override val value: String = message.role.name.lowercase()
    }

    data class PromptIndexedContent(
        private val index: Int,
        private val message: ai.koog.prompt.message.Message.Request,
    ) : Prompt {
        override val key: String = super.key.concatKey("${index}.content")
        override val value: String = message.content
    }

    data class PromptIndexedMessagesRole(
        private val index: Int,
        private val message: ai.koog.prompt.message.Message.Request,
    ) : Prompt {
        override val key: String = super.key.concatKey("${index}.message.role")
        override val value: String = message.role.name.lowercase()
    }

    data class PromptIndexedMessagesContent(
        private val index: Int,
        private val message: ai.koog.prompt.message.Message.Request,
    ) : Prompt {
        override val key: String = super.key.concatKey("${index}.message.content")
        override val value: String = message.content
    }

    // completion
    sealed interface Completion : GenAIAttribute {
        override val key: String
            get() = super.key.concatKey("completion")
    }

    data class CompletionIndexedRole(
        private val index: Int,
        private val message: ai.koog.prompt.message.Message.Response,
    ) : Prompt {
        override val key: String = super.key.concatKey("${index}.role")
        override val value: String = message.role.name.lowercase()
    }

    data class CompletionIndexedContent(
        private val index: Int,
        private val message: ai.koog.prompt.message.Message.Response,
    ) : Prompt {
        override val key: String = super.key.concatKey("${index}.content")
        override val value: String = message.content
    }

    data class CompletionIndexedMessagesRole(
        private val index: Int,
        private val message: ai.koog.prompt.message.Message.Response,
    ) : Prompt {
        override val key: String = super.key.concatKey("${index}.message.role")
        override val value: String = message.role.name.lowercase()
    }

    data class CompletionIndexedMessagesContent(
        private val index: Int,
        private val message: ai.koog.prompt.message.Message.Response,
    ) : Prompt {
        override val key: String = super.key.concatKey("${index}.message.content")
        override val value: String = message.content
    }

    // tool



}
