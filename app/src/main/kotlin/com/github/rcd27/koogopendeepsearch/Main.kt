package com.github.rcd27.koogopendeepsearch

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import com.github.rcd27.koogopendeepsearch.agent.executor.openAISinglePromptExecutor
import com.github.rcd27.koogopendeepsearch.agent.strategy.deepResearchStrategy
import com.github.rcd27.koogopendeepsearch.agent.strategy.subgraphResearcher
import com.github.rcd27.koogopendeepsearch.agent.tools.thinkTool


fun standaloneResearchStrategy(conversationPrompt: Prompt) =
    strategy<String, String>("standalone_research_strategy") {
        val emulateChatHistory by node<String, String>("emulate_message_history") {
            llm.writeSession {
                prompt = conversationPrompt
            }
            "<bypass/>"
        }
        val researcher: AIAgentNodeBase<String, String> by subgraphResearcher()
        nodeStart then emulateChatHistory then researcher then nodeFinish
    }

object DeepResearchAgent {
    val agentConfig = AIAgentConfig.withSystemPrompt(
        prompt = "You are deep research agent",
        maxAgentIterations = 50,
        llm = OpenAIModels.CostOptimized.GPT4oMini // TODO: check model in langchain implementation
    )

    suspend inline fun <reified INPUT, reified OUTPUT> withStrategy(
        strategy: AIAgentGraphStrategy<INPUT, OUTPUT>
    ): GraphAIAgent<INPUT, OUTPUT> {
        val tavilyMcpToolRegistry = run {
            val tavilyMcpProcess = ProcessBuilder(
                "env",
                "TAVILY_API_KEY=${Config.TAVILY_API_KEY}",
                "npx",
                "-y",
                "tavily-mcp@latest"
            ).start()

            val tavilyTransport = McpToolRegistryProvider.defaultStdioTransport(tavilyMcpProcess)

            McpToolRegistryProvider.fromTransport(
                transport = tavilyTransport,
                name = "tavily-mcp",
                version = "1.0.0"
            )
        }

        val tavilySearchTool = tavilyMcpToolRegistry.tools.filter { it.name == "tavily-search" }[0]

        val researchTools = ToolRegistry {
            tool(::thinkTool.asTool())
            tool(tavilySearchTool)
        }

        val agent: GraphAIAgent<INPUT, OUTPUT> = AIAgent(
            promptExecutor = openAISinglePromptExecutor,
            agentConfig = agentConfig,
            toolRegistry = researchTools,
            strategy = strategy,
        ) {
            install(OpenTelemetry) {
                setVerbose(true) // to see system/user prompts
                // see: https://docs.koog.ai/opentelemetry-langfuse-exporter/
                addLangfuseExporter(
                    langfuseUrl = Config.LANGFUSE_HOST,
                    langfusePublicKey = Config.LANGFUSE_PUBLIC_KEY,
                    langfuseSecretKey = Config.LANGFUSE_SECRET_KEY,
                    traceAttributes = listOf(
                        CustomAttribute("langfuse.trace.input", "TEST_INPUT"), // TODO: get actual input
                        CustomAttribute("langfuse.trace.output", "TEST_OUTPUT") // TODO: get actual output
                    )
                )
            }
        }
        return agent
    }
}

suspend fun main() {
    val strategy = deepResearchStrategy(
        askUserTool = { question ->
            println(question)
            print("Answer: ")
            readln()
        }
    )
    val agent = DeepResearchAgent.withStrategy(strategy)
    val executionResult = agent.run("")
    println(executionResult)
}
