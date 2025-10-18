package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.feature.withTesting
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test

class AgentGraphTest {

    @Test
    fun `Test clarify_with_user subgraph succeeds if needClarification=false`() {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer(
                Json.encodeToString(
                    ClarifyWithUser(
                        false,
                        "",
                        "I totally understand what research you need"
                    )
                )
            ).asDefaultResponse
        }
        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = deepResearchStrategy { "STUB" },
            agentConfig = AIAgentConfig(
                prompt = prompt("test") {},
                model = OpenAIModels.Chat.GPT4o,
                maxAgentIterations = 100
            ),
            toolRegistry = ToolRegistry.EMPTY
        ) {
            withTesting {
                verifyStrategy<String, String>("deep_research") {
                    val clarifyWithUser = assertSubgraphByName<String, String>("clarify_with_user")
                    verifySubgraph(clarifyWithUser)
                }
                enableGraphTesting = true
            }
        }
        runBlocking {
            agent.run("test_agentInput")
        }
    }
}