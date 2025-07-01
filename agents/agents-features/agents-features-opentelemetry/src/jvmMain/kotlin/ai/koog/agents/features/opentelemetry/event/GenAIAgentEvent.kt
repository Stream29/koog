package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttribute

internal interface GenAIAgentEvent {

    val verbose: Boolean

    val name: String
        get() = "gen_ai"

    val attributes: List<GenAIAttribute>

    fun String.concatName(other: String): String = "$this.$other"



}