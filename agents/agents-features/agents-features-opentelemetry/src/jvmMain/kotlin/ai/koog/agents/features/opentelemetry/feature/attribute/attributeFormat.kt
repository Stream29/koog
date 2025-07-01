package ai.koog.agents.features.opentelemetry.feature.attribute

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.message.Message

internal val ToolDescriptor.spanString: String
    get() = "{name: ${this.toolNameString}, args:{${this.toolArgsString}}}"

internal val ToolDescriptor.toolNameString: String
    get() = this.name

internal val ToolDescriptor.toolArgsString: String
    get() = "required_parameters: [${this.requiredParameters.joinToString { "${it.name}:${it.type.name}" }}], " +
        "optional_parameters: [${this.optionalParameters.joinToString { "${it.name}:${it.type.name}" }}]"

internal val Message.Role.spanString: String
    get() = this.name.lowercase()
