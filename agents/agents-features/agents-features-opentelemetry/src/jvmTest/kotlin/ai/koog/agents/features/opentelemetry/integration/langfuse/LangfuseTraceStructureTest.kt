package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.features.opentelemetry.integration.TraceStructureTestBase

/**
 * Test traces OpenTelemetry structure conformance to Langfuse and Weave data models.
 */
class LangfuseTraceStructureTest : TraceStructureTestBase({ addLangfuseExporter() })
