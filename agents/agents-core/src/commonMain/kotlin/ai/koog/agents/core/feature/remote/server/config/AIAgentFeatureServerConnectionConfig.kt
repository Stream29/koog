package ai.koog.agents.core.feature.remote.server.config

import ai.koog.agents.core.feature.agentFeatureMessageSerializersModule
import ai.koog.agents.features.common.remote.server.config.ServerConnectionConfig

/**
 * Configuration class for setting up an agent feature server connection.
 * Properties:
 * host - The host on which the server will listen to.
 * port - The port number on which the server will listen to.
 */
public class AIAgentFeatureServerConnectionConfig(host: String, port: Int, waitConnection: Boolean = false) :
    ServerConnectionConfig(host = host, port = port, waitConnection = waitConnection) {

    init {
        appendSerializersModule(agentFeatureMessageSerializersModule)
    }
}
