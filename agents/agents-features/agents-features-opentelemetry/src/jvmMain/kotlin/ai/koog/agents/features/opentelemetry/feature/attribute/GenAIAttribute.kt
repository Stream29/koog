package ai.koog.agents.features.opentelemetry.feature.attribute

internal sealed interface GenAIAttribute {
    val key: String
        get() = "gen_ai"

    val value: Any

    fun String.concatKey(other: String) = this.plus(".${other}")
}
