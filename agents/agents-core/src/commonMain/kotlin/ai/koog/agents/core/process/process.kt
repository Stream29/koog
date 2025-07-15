package ai.koog.agents.core.process

/**
 * Retrieves the arguments passed to the currently running process.
 *
 * @return A list of strings representing the process arguments, or null if the arguments cannot be retrieved.
 */
public expect fun getCurrentProcessArgs(): List<String>?
