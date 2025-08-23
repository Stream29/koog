# Unreleased

> Changes since 0.3.0

## Features

### Observability and Tracing
- **KG-223**: Add support for Langfuse graphs visualization for node spans (cc67a7a)
- **KG-218**: Add finish reason attributes to spans and events (7558b44)
- **KG-218**: Extract all event body fields on OT to event attributes (6f5235c)
- **KG-218**: Fix attributes for Inference and Tool spans (d19d913)
- **KG-218**: Always add Role attribute to message spans (7e3f3a2)
- **KG-217**: Implement span adapters for Langfuse and Weave clients (0c92e85)
- **KG-217**: Add a span adapter for the Open Telemetry feature (e6b54c4)
- **KG-172**: Add all messages to the inference span for Open Telemetry (33d7b62)
- **KG-175**: Add input.value and output.value attributes for ExecuteToolSpan (d08f1fd)
- **KG-187**: Add Debugger feature (ea30cd9)
- **KG-186**: Add a flag for the feature remote server to wait for connection to continue (44b83f5)
- **KG-186**: Move common feature logic from agents-features-common into agents-core module (cae75c9)
- **KG-169**: Add support for setting the OpenTelemetry SDK in koog (3f5d6f1)
- **KG-170**: Update existing features with handling NodeExecutionError agent event (66a6aeb)
- **KG-170**: Add AIAgentPipeline interceptors for errors in nodes (ff5281d)
- **KG-164**: Fix tool serialization when sending events with Tracing a remote writer (d5b7950)
- **KG-174**: Fix failures for remote writer tests caused by timeout (281d8d7)
- Add tool arguments for gen_ai.choice and gen_ai.assistant.message events (#462) (aa4a2e8)
- Add index for ChoiceEvent in OpenTelemetry (0aa98bd)

### Agents
- **KG-266**: Remove verbosity configuration from adapters (025699b)
- **KG-259**: Update type for 'input.value' and 'output.value' attributes for ExecuteToolSpan (1260245)
- **KG-259**: Mask sensitive data in OT events and attributes (bb2daa8)
- **KG-259**: Add a special type for strings hidden by default (4ad0e89)
- **KG-173**: Move choice prompt executor logic into a separate module (fcf0c74)
- Include the finish node processing (#598) (458c34c)
- Introduce Retry logic for LLM clients (#592) (5601cb4)
- Add Ktor integration via `Koog` ktor plugin (#422) (22c37c4)
- Add the feedback mechanism for the retry component (#459) (be5a9b2)

### Provider Support
- Refactor OpenAI data model schemas (#517) (87a9439)
- Refactor structured output API improving flexibility, support native structured output for OpenAI and Google (#443) (c0ca25d)
- Add support for Google's "thinking" mode in generation config (#414) (0ad5994)
- Add maxTokens as prompt parameters (#579) (38a8424)
- Support `AWS_SESSION_TOKEN` in BedrockClient (#456) (958aa0c)
- Fix for urls generated from AzureOpenAIClientSettings (#478) (d3fe0ba)
- Update document capabilities for LLModel (#543) (1d78f42)

## Bug Fixes
- **KG-221**: Add an edit button to the docs (#597) (6e968d5)
- **KG-216**: Remove Gemini Flash and Pro 1.5 (#574) (d99958b)
- **KG-256**: Use Gemini2_0Flash instead of Gemini2_5Pro (#587) (8b7804e)
- **KG-101**: Fix NumberGuessingAgent Example (#445) (42cfaa7)
- Fix GPT 4o-mini vs o4-mini confusion (#573) (eca87e8)
- Fix duplicated tool names in AIAgentSubgraphExt (#493) (c819fcb)
- Fix Anthropic json schema validation error (#457) (bb35268)

## Improvements
- Drop extensions functions for PromptExecutor (#591) (2663e83)
- Reworked FileSystemProvider (#557) (68d5962)
- Rename `fromAbsoluteString` to `fromAbsolutePathString` (#567) (2877f4a)
- Move suspendable operations to Dispatchers.IO context (adb738a)
- Remove deprecated methods from FileSystemProvider (181b6fe)
- Remove deprecated FSProvider interfaces (740fa77)
- Rename PathFilter to TraversalFilter and make its methods suspendable (c29de2d)
- Make FilteredFileSystemProvider accept custom path filters (#508) (ec7b530)
- Introduce `filterByRoot` extensions for FS providers for easier agent's environment setup (#494) (35b6734)
- Update FileSystemProvider specification and JVM implementation + cover with tests (#388) (362ea10)
- Extract content metadata into a separate method (#550) (956e5b8)
- Add input validation and corresponding tests to LLMParams (#515) (3befab2)
- Add validation for InMemoryPromptCache configurations (#467) (be9c282)
- Fix InMemoryPromptCache validation logic and update tests (#502) (b77dc3a)
- Add tests for invalid ToolDescriptor validation (#507) (423dc54)
- Add LLModel contextLength and maxOutputTokens (#438) (fde9ac9)
- Langfuse and Weave configurations moved to the library (d3baf87)
- Update mcp dependency to v0.6.0 (#523) (a580a0c)
- Update messageProcessor parameter name in the FeatureConfig class (10bd485)
- Rename missing strategyId properties to strategyName (dc54579)
- Fix linter errors in the codebase (c0a16695)
- Add references to extensions (1f644fb)

## Examples
- [examples] Add trip planning agent (#595) (cb5da25)
- [examples] Add web search agent from Koog live stream (#575) (7f69dba)
- Improved BestJokeAgent sample a bit (#503) (c1de9c0)

# 0.3.0

> Published 15 Jul 2025

## Major Features

- **Agent Persistency and Checkpoints**: Save and restore agent state to local disk, memory, or easily integrate with
  any cloud storages or databases. Agents can now roll back to any prior state on demand or automatically restore from
  the latest checkpoint (#305)
- **Vector Document Storage**: Store embeddings and documents in persistent storage for retrieval-augmented generation (
  RAG), with in-memory and local file implementations (#272)
- **OpenTelemetry Support**: Native integration with OpenTelemetry for unified tracing logs across AI agents (#369, #401,
  #423, #426)
- **Content Moderation**: Built-in support for moderating models, enabling AI agents to automatically review and filter
  outputs for safety and compliance (#395)
- **Parallel Node Execution**: Parallelize different branches of your agent graph with a MapReduce-style API to speed up
  agent execution or to choose the best of the parallel attempts (#220, #404)
- **Spring Integration**: Ready-to-use Spring Boot starter with auto-configured LLM clients and beans (#334)
- **AWS Bedrock Support**: Native support for Amazon Bedrock provider covering several crucial models and services (
  #285, #419)
- **WebAssembly Support**: Full support for compiling AI agents to WebAssembly (WASM) for browser deployment (#349)

## Improvements

- **Multimodal Data Support**: Seamlessly integrate and reason over diverse data types such as text, images, and audio (
  #277)
- **Arbitrary Input/Output Types**: More flexibility over how agents receive data and produce responses (#326)
- **Improved History Compression**: Enhanced fact-retrieval history compression for better context management (#394,
  #261)
- **ReAct Strategy**: Built-in support for ReAct (Reasoning and Acting) agent strategy, enabling step-by-step reasoning
  and dynamic action taking (#370)
- **Retry Component**: Robust retry mechanism to enhance agent resilience (#371)
- **Multiple Choice LLM Requests**: Generate or evaluate responses using structured multiple-choice formats (#260)
- **Azure OpenAI Integration**: Support for Azure OpenAI services (#352)
- **Ollama Enhancements**: Native image input support for agents running with Ollama-backed models (#250)
- **Customizable LLM in fact search**: Support providing custom LLM for fact retrieval in the history (#289)
- **Tool Execution Improvements**: Better support for complex parameters in tool execution (#299, #310)
- **Agent Pipeline enhancements**: More handlers and context available in `AIAgentPipeline` (#263)
- **Default support of tools and messages mixture**: Simple single run strategies variants for multiple message and
  parallel tool calls (#344)
- **ResponseMetaInfo Enhancement**: Add `additionalInfo` field to `ResponseMetaInfo` (#367)
- **Subgraph Customization**: Support custom `LLModel` and `LLMParams` in subgraphs, make `nodeUpdatePrompt` a
  pass-through node (#354)
- **Attachments API simplification**: Remove additional `content` builder from `MessageContentBuilder`, introduce
  `TextContentBuilderBase` (#331)
- **Nullable MCP parameters**: Added support for nullable MCP tool parameters (#252)
- **ToolSet API enhancement**: Add missing `tools(ToolSet)` convenience method for `ToolRegistry` builder (#294)
- **Thinking support in Ollama**: Add THINKING capability and it's serialization for Ollama API 0.9 (#248)
- **kotlinx.serialization version update**: Update kotlinx-serialization version to 1.8.1
- **Host settings in FeatureMessageRemoteServer**: Allow configuring custom host in `FeatureMessageRemoteServer`  (#256)
  Victor Sima* 6/10/25, 20:32

## Bug Fixes

- Make `CachedPromptExecutor` and `PromptCache` timestamp-insensitive to enable correct caching (#402)
- Fix `requestLLMWithoutTools` generating tool calls (#325)
- Fix Ollama function schema generation from `ToolDescriptor` (#313)
- Fix OpenAI and OpenRouter clients to produce simple text user message when no attachments are present (#392)
- Fix intput/output token counts for OpenAILLMClient (#370)
- Using correct `Ollama` LLM provider for ollama llama4 model (#314)
- Fixed an issue where structured data examples were prompted incorrectly (#325)
- Correct mistaken model IDs in DEFAULT_ANTHROPIC_MODEL_VERSIONS_MAP (#327)
- Remove possibility of calling tools in structured LLM request (#304)
- Fix prompt update in `subgraphWithTask` (#304)
- Removed suspend modifier from LLMClient.executeStreaming (#240)
- Fix `requestLLMWithoutTools` to work properly across all providers (#268)

## Examples

- W&B Weave Tracing example
- LangFuse Tracing example
- Moderation example: Moderating iterative joke-generation conversation
- Parallel Nodes Execution example: Generating jokes using 3 different LLMs in parallel, and choosing the funniest one
- Snapshot and Persistency example: Taking agent snapshots and restoring its state example

# 0.2.1

> Published 6 Jun 2025

## Bug Fixes

- Support MCP enum arg types and object additionalParameters (#214)
- Allow appending handlers for the EventHandler feature (#234)
- Migrating of simple agents to AIAgent constructor, simpleSingleRunAgent deprecation (#222)
- Fix LLM clients after #195, make LLM request construction again more explicit in LLM clients (#229)

# 0.2.0

> Published 5 Jun 2025

## Features

- Add media types (image/audio/document) support to prompt API and models (#195)
- Add token count and timestamp support to Message.Response, add Tokenizer and MessageTokenizer feature (#184)
- Add LLM capability for caching, supported in anthropic mode (#208)
- Add new LLM configurations for Groq, Meta, and Alibaba (#155)
- Extend OpenAIClientSettings with chat completions API path and embeddings API path to make it configurable (#182)

## Improvements

- Mark prompt builders with PromptDSL (#200)
- Make LLM provider not sealed to allow it's extension (#204)
- Ollama reworked model management API (#161)
- Unify PromptExecutor and AIAgentPipeline API for LLMCall events (#186)
- Update Gemini 2.5 Pro capabilities for tool support
- Add dynamic model discovery and fix tool call IDs for Ollama client (#144)
- Enhance the Ollama model definitions (#149)
- Enhance event handlers with more available information (#212)

## Bug Fixes

- Fix LLM requests with disabled tools, fix prompt messages update (#192)
- Fix structured output JSON descriptions missing after serialization (#191)
- Fix Ollama not calling tools (#151)
- Pass format and options parameters in Ollama request DTO (#153)
- Support for Long, Double, List, and data classes as tool arguments for tools from callable functions (#210)

## Examples

- Add demo Android app to examples (#132)
- Add example with media types - generating Instagram post description by images (#195)

## Removals

- Remove simpleChatAgent (#127)

# 0.1.0 (Initial Release)

> Published 21 May 2025

The first public release of Koog, a Kotlin-based framework designed to build and run AI agents entirely in idiomatic
Kotlin.

## Key Features

- **Pure Kotlin implementation**: Build AI agents entirely in natural and idiomatic Kotlin
- **MCP integration**: Connect to Model Context Protocol for enhanced model management
- **Embedding capabilities**: Use vector embeddings for semantic search and knowledge retrieval
- **Custom tool creation**: Extend your agents with tools that access external systems and APIs
- **Ready-to-use components**: Speed up development with pre-built solutions for common AI engineering challenges
- **Intelligent history compression**: Optimize token usage while maintaining conversation context
- **Powerful Streaming API**: Process responses in real-time with streaming support and parallel tool calls
- **Persistent agent memory**: Enable knowledge retention across sessions and different agents
- **Comprehensive tracing**: Debug and monitor agent execution with detailed tracing
- **Flexible graph workflows**: Design complex agent behaviors using intuitive graph-based workflows
- **Modular feature system**: Customize agent capabilities through a composable architecture
- **Scalable architecture**: Handle workloads from simple chatbots to enterprise applications
- **Multiplatform**: Run agents on both JVM and JS targets with Kotlin Multiplatform

## Supported LLM Providers

- Google
- OpenAI
- Anthropic
- OpenRouter
- Ollama

## Supported Targets

- JVM (requires JDK 17 or higher)
- JavaScript
