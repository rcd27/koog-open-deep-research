package com.github.rcd27.koogopendeepsearch

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import com.github.rcd27.koogopendeepsearch.agent.executor.openAISinglePromptExecutor
import com.github.rcd27.koogopendeepsearch.agent.strategy.deepResearchStrategy
import com.github.rcd27.koogopendeepsearch.agent.tools.thinkTool

suspend fun main() {
    val agentConfig = AIAgentConfig.withSystemPrompt(
        prompt = "You are deep research agent",
        maxAgentIterations = 50,
        llm = OpenAIModels.CostOptimized.GPT4oMini // TODO: check model in langchain implementation
    )

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
    }

    // see: https://docs.koog.ai/parallel-node-execution/

    val agent = AIAgent(
        promptExecutor = openAISinglePromptExecutor,
        strategy = deepResearchStrategy(
            askUserTool = { question ->
                println(question)
                print("Answer: ")
                readln()
            }
        ),
        agentConfig = agentConfig,
        toolRegistry = researchTools
    ) {
        install(OpenTelemetry) {
            setVerbose(true) // to see system/user prompts
            // see: https://docs.koog.ai/opentelemetry-langfuse-exporter/
            addLangfuseExporter(
                langfuseUrl = Config.LANGFUSE_HOST,
                langfusePublicKey = Config.LANGFUSE_PUBLIC_KEY,
                langfuseSecretKey = Config.LANGFUSE_SECRET_KEY
            )
        }
    }
    val executionResult = agent.run("")
    println(executionResult)
}
