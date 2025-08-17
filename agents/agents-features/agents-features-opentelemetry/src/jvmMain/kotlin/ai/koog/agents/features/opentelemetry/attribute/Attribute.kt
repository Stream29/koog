package ai.koog.agents.features.opentelemetry.attribute

internal interface Attribute {
    val key: String
    val value: Any
    val sensitive: Boolean

    fun String.concatKey(other: String) = this.plus(".$other")
}
