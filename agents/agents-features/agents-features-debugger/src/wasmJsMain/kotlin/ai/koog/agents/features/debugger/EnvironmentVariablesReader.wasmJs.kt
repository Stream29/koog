package ai.koog.agents.features.debugger

import io.github.oshai.kotlinlogging.KotlinLogging

private fun getEnvironmentVariableInternal(name: String): String? = js("process.env[name]")

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual object EnvironmentVariablesReader {

    private val logger = KotlinLogging.logger { }

    internal actual fun getEnvironmentVariable(name: String): String? {
        logger.debug { "Getting environment variable '$name'" }

        val envVarValue = getEnvironmentVariableInternal(name)
        logger.debug { "Got environment variable '$name' value: '$envVarValue'" }

        return envVarValue
    }
}
