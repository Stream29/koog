package ai.koog.agents.features.debugger.feature

import ai.koog.agents.core.feature.config.FeatureConfig

public class DebuggerConfig : FeatureConfig() {

    private var _port: Int? = null

    public val port: Int?
        get() = _port

    public fun setPort(port: Int) {
        _port = port
    }
}