package ai.koog.agents.mcp.parser

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.*

/**
 * Default implementation of [McpToolDescriptorParser].
 */
public object DefaultMcpToolDescriptorParser : McpToolDescriptorParser {

    private val logger = KotlinLogging.logger(DefaultMcpToolDescriptorParser::class.qualifiedName ?: "ai.koog.agents.mcp.parser.DefaultMcpToolDescriptorParser")

    /**
     * Parses an MCP SDK Tool definition into tool descriptor format.
     *
     * This method extracts tool information (name, description, parameters) from an MCP SDK Tool
     * and converts it into a ToolDescriptor that can be used by the agent framework.
     *
     * @param sdkTool The MCP SDK Tool to parse.
     * @return A ToolDescriptor representing the MCP tool.
     */
    override fun parse(sdkTool: Tool): ToolDescriptor {

        val parameters: List<ToolParameterDescriptor> = parseParameters(sdkTool.inputSchema.properties)
        val requiredParameters: List<String> = sdkTool.inputSchema.required ?: emptyList()

        return ToolDescriptor(
            name = sdkTool.name,
            description = sdkTool.description.orEmpty(),
            requiredParameters = parameters.filter { requiredParameters.contains(it.name) },
            optionalParameters = parameters.filter { !requiredParameters.contains(it.name) },
        )
    }

    //region Private Methods

    private fun parseParameters(properties: JsonObject): List<ToolParameterDescriptor> {
        return properties.mapNotNull { (name, element) ->
            require(element is JsonObject) {
                "Parameter '$name' must be a JSON object"
            }

            val description = element["description"]?.jsonPrimitive?.content.orEmpty()
            val type = parseParameterType(element)

            ToolParameterDescriptor(name = name, description = description, type = type)
        }
    }

    private fun parseParameterType(element: JsonObject): ToolParameterType {

        val typeStr = element["type"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Parameter type must have 'type' property.")

        val type = when (typeStr.trim().lowercase()) {
            "string" -> ToolParameterType.String
            "integer" -> ToolParameterType.Integer
            "number" -> ToolParameterType.Float
            "boolean" -> ToolParameterType.Boolean
            "enum" -> element.toParameterEnum()
            "array" -> element.toParameterList()
            "object" -> element.toParameterObject()

            else -> throw IllegalArgumentException("Unsupported parameter type: '$typeStr'")
        }

        logger.debug { "Successfully converted parameter '$typeStr' into parameter type: '$type'" }
        return type
    }

    private fun JsonObject.toParameterEnum() : ToolParameterType {
        return ToolParameterType.Enum(
            this.getValue("enum").jsonArray.map { it.jsonPrimitive.content }.toTypedArray()
        )
    }

    private fun JsonObject.toParameterList() : ToolParameterType {
        val items = this["items"]?.jsonObject
            ?: throw IllegalArgumentException("Array type parameters must have items property")

        val itemType = parseParameterType(items)

        return ToolParameterType.List(itemsType = itemType)
    }

    private fun JsonObject.toParameterObject() : ToolParameterType {

        val properties = this["properties"]?.let { element ->
            element.jsonObject.map { (name, property) ->
                // Description is optional
                val description = this["description"]?.jsonPrimitive?.content.orEmpty()
                ToolParameterDescriptor(name, description, parseParameterType(property.jsonObject))
            }
        } ?: emptyList()

        val requiredProperties = this["required"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

        val additionalProperties = this["additionalProperties"]?.let { element ->
            when (element) {
                is JsonPrimitive -> this.getValue("additionalProperties").jsonPrimitive.boolean
                is JsonObject -> true
                else -> null
            }
        }

        val additionalPropertiesType = this["additionalProperties"]?.let { element ->
            when (element) {
                is JsonObject -> parseParameterType(this.getValue("additionalProperties").jsonObject)
                else -> null
            }
        }

        return ToolParameterType.Object(
            properties = properties,
            requiredProperties = requiredProperties,
            additionalPropertiesType = additionalPropertiesType,
            additionalProperties = additionalProperties
        )
    }

    //endregion Private Methods
}