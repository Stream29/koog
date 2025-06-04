package ai.koog.agents.a2a.core.models

import kotlinx.serialization.Serializable

/**
 * Represents an agent's identity, capabilities, and available skills
 * as defined by the A2A protocol specification.
 */
@Serializable
data class AgentCard(
    val name: String,
    val description: String,
    val version: String,
    val capabilities: List<String>,
    val skills: List<Skill>,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Represents a skill that an agent can perform.
 */
@Serializable
data class Skill(
    val name: String,
    val description: String,
    val parameters: JsonSchema,
    val returns: JsonSchema? = null,
    val examples: List<SkillExample> = emptyList()
)

/**
 * Example usage of a skill.
 */
@Serializable
data class SkillExample(
    val input: Map<String, String>,
    val output: String,
    val description: String? = null
)

/**
 * Simple JSON Schema representation for skill parameters.
 * This can be extended to support full JSON Schema validation in the future.
 */
@Serializable
data class JsonSchema(
    val type: String,
    val properties: Map<String, JsonSchemaProperty> = emptyMap(),
    val required: List<String> = emptyList(),
    val description: String? = null
)

/**
 * Property definition in a JSON Schema.
 */
@Serializable
data class JsonSchemaProperty(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null,
    val format: String? = null
)