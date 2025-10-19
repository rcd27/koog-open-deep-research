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
            // #1 When: initial prompt contains empty chat history
            mockLLMAnswer(
                Json.encodeToString(
                    ClarifyWithUser(
                        needClarification = true,
                        question = "Which specific insights do you want to receive?",
                        verification = ""
                    )
                )
            ).onRequestContains("<messages>\n<previous_conversation/>\n</messages>")
            // #2 When: user clarifies topic etc.
            mockLLMAnswer(
                Json.encodeToString(
                    ClarifyWithUser(
                        false,
                        "",
                        "I totally understand what research you need"
                    )
                )
            ).onRequestContains("Is it possible to live on Mars?")
        }

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            // If clarification needed, subgraph calls askUserTool
            strategy = deepResearchStrategy { "Is it possible to live on Mars?" },
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
                    // You need to have at least one verification
                    verifySubgraph(clarifyWithUser)
                }
                // Make sure you have `verifyStrategy` block not to catch NPE from agents-test
                enableGraphTesting = true
            }
        }
        runBlocking {
            agent.run("test_agentInput")
        }
    }
}