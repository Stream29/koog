package ai.koog.agents.features.common.remote.server.config

import ai.koog.agents.features.common.remote.ConnectionConfig

/**
 * Configuration class for setting up a server connection.
 *
 * @property host The host on which the server will listen to. Defaults to 127.0.0.1 (localhost);
 * @property port The port number on which the server will listen to. Defaults to 8080;
 * @property jsonConfig The effective JSON configuration to be used, falling back to a default configuration
 *                      if a custom configuration is not provided;
 */
public abstract class ServerConnectionConfig(
    public val host: String,
    public val port: Int,
    public val wait: Boolean,
) : ConnectionConfig()
