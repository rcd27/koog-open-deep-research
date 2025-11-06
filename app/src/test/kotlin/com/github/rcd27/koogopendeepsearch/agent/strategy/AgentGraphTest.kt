package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.feature.withTesting
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test

// FIXME: NoSuchMethodException
class AgentGraphTest {

    @Test
    fun `Test deepResearchStrategy`() {
        val mockExecutor = getMockExecutor {
            // #1 Then: LLM asks for clarification
            mockLLMAnswer(
                Json.encodeToString(
                    ClarifyWithUser(
                        needClarification = true,
                        question = "Which specific insights do you want to receive?",
                        verification = ""
                    )
                )
                // #1 When: initial prompt contains empty chat history
            ).onRequestContains("<messages>\n<previous_conversation/>\n</messages>")
            // #2 Then: LLM decides it has enough info to proceed
            mockLLMAnswer(
                Json.encodeToString(
                    ClarifyWithUser(
                        false,
                        "",
                        "I totally understand what research you need"
                    )
                )
                // #2 When: user clarifies the topic etc.
            ).onRequestContains(
                """
                <messages>
                <previous_conversation>
                  <user>
                    Is it possible to live on Mars?
                  </user>
                </previous_conversation>
                </messages>
                """.trimIndent()
            )
            // #3 Then: LLM generates a brief topic for research
            mockLLMAnswer(
                Json.encodeToString(
                    ResearchQuestion(
                        "What are the key scientific, environmental, and technological challenges involved in establishing a sustainable human presence on Mars, and what are the potential solutions or advancements needed to address these challenges?"
                    )
                )
                // #3 When: LLM is asked for generating research brief
            ).onRequestContains("""The messages that have been exchanged so far between yourself and the user are:""".trimIndent())
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

                    /** SKIPPED: supervisor */

                    val researcher = assertSubgraphByName<String, String>("researcher")
                    verifySubgraph(researcher)
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
