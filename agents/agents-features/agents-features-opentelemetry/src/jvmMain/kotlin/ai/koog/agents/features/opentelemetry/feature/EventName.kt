package ai.koog.agents.features.opentelemetry.feature

internal enum class EventName(val id: String) {

    AGENT_BEFORE_START("agent.before.start"),
    AGENT_FINISHED("agent.finished")

}