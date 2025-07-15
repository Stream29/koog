package ai.koog.agents.core.process

import kotlin.jvm.optionals.getOrNull

/**
 * Retrieves the arguments passed to the current process.
 *
 * @return A list of strings representing the arguments passed to the current process, or null if the arguments cannot be determined.
 */
public actual fun getCurrentProcessArgs(): List<String>? {
    return ProcessHandle.current().info().arguments().getOrNull()?.toList()
}