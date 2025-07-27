package ai.koog.agents.features.common.remote.server.config

import ai.koog.agents.features.common.remote.ConnectionConfig

/**
 * Configuration class for setting up a server connection.
 *
 * @property host The host on which the server will listen to. Defaults to 127.0.0.1 (localhost);
 * @property port The port number on which the server will listen to. Defaults to 8080;
 * @property waitConnection Defines whether the server needs to wait for the first connection from a client.
 */
public abstract class ServerConnectionConfig(
    public val host: String,
    public val port: Int,
    public val waitConnection: Boolean,
) : ConnectionConfig()
