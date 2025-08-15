package ai.koog.agents.features.opentelemetry

import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal fun assertMapsEqual(expected: Map<*, *>, actual: Map<*, *>, message: String = "") {
    assertEquals(expected.size, actual.size, "$message - Map sizes should be equal")

    expected.forEach { (key, value) ->
        assertTrue(actual.containsKey(key), "$message - Key '$key' should exist in actual map")
        assertEquals(value, actual[key], "$message - Value for key '$key' should match")
    }
}
