package ai.koog.agents.features.debugger

import io.github.oshai.kotlinlogging.KotlinLogging

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual object EnvironmentVariablesReader {

    private val logger = KotlinLogging.logger { }

    internal actual fun getEnvironmentVariable(name: String): String? {
        logger.debug { "Getting environment variable '$name'" }
        return js("process.env[name]")
    }
}