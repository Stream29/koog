package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.tools.ToolDescriptor

internal val ToolDescriptor.spanElement: String
    get() = "{name: ${this.name}, required_parameters: [${this.requiredParameters.joinToString { "${it.name}:${it.type.name}" }}]}"