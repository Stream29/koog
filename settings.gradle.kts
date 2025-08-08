rootProject.name = "koog-agents"

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

include(":agents:agents-core")
include(":agents:agents-ext")

include(":agents:agents-features:agents-features-debugger")
include(":agents:agents-features:agents-features-event-handler")
include(":agents:agents-features:agents-features-memory")
include(":agents:agents-features:agents-features-opentelemetry")
include(":agents:agents-features:agents-features-trace")
include(":agents:agents-features:agents-features-tokenizer")
include(":agents:agents-features:agents-features-snapshot")

include(":agents:agents-mcp")
include(":agents:agents-test")
include(":agents:agents-tools")
include(":agents:agents-utils")

include(":examples")

include(":integration-tests")

include(":koog-agents")

include(":prompt:prompt-cache:prompt-cache-files")
include(":prompt:prompt-cache:prompt-cache-model")
include(":prompt:prompt-cache:prompt-cache-redis")

include(":prompt:prompt-executor:prompt-executor-cached")

include(":koog-models")
include(":koog-models:anthropic")
include(":koog-models:bedrock")
include(":koog-models:deepseek")
include(":koog-models:google")
include(":koog-models:ollama")
include(":koog-models:openai")
include(":koog-models:openai-chatmodel")
include(":koog-models:openrouter")

include(":prompt:prompt-executor:prompt-executor-llms")
include(":prompt:prompt-executor:prompt-executor-llms-all")
include(":prompt:prompt-executor:prompt-executor-model")

include(":prompt:prompt-llm")
include(":prompt:prompt-markdown")
include(":prompt:prompt-model")
include(":prompt:prompt-structure")
include(":prompt:prompt-tokenizer")
include(":prompt:prompt-xml")

include(":embeddings:embeddings-base")
include(":embeddings:embeddings-llm")

include(":rag:rag-base")
include(":rag:vector-storage")

include(":koog-spring-boot-starter")

include(":koog-ktor")
