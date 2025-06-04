package ai.koog.agents.a2a.client

import ai.koog.agents.a2a.core.A2AClient

/**
 * Platform-specific A2A client factory.
 */
expect object PlatformA2AClientFactory {
    fun createPlatformDefault(
        config: A2AClientConfig = A2AClientConfig(),
        enableLogging: Boolean = false
    ): A2AClient
}