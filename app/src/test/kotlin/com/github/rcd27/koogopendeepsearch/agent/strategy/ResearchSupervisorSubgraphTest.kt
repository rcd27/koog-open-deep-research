package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class ResearchSupervisorSubgraphTest {

    @Test
    fun `supervisor subgraph plans, runs researchers in parallel, tracks history, and completes`() = runBlocking {
        val researchBrief = "Test brief: Compare Model A and Model B for energy efficiency."

        // Mock LLM executor sequence:
        // 1) Plan -> ConductResearch with two subtopics
        // 2) Reflection -> plain text
        // 3) Plan -> ResearchComplete
        val mockExecutor = getMockExecutor {
            // First planning call: no history -> contains "<empty>"
            mockLLMAnswer(
                Json.encodeToString(
                    SupervisorPlan(
                        action = "ConductResearch",
                        subtopics = listOf("Topic A", "Topic B")
                    )
                )
            ).onRequestContains("<empty>")

            // Reflection call
            mockLLMAnswer(
                "Supervisor reflection: assessing results"
            ).onRequestContains("Reflect on the current research progress")

            // Second planning call: should include Iteration #1 in history
            mockLLMAnswer(
                Json.encodeToString(
                    SupervisorPlan(
                        action = "ResearchComplete",
                        finalSummary = "Done"
                    )
                )
            ).onRequestContains("Iteration #1:")
        }

        val fakeRunner: suspend (String) -> String = { topic ->
            // Simulated researcher output
            "Result for $topic"
        }

        val strategy = strategy<String, String>("supervisor_only") {
            val supervisor by subgraphResearchSupervisor(
                researcherRunner = fakeRunner,
                maxIterations = 3,
                maxConcurrent = 2
            )
            nodeStart.then(supervisor).then(nodeFinish)
        }

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = AIAgentConfig(
                prompt = prompt("test") {},
                model = OpenAIModels.Chat.GPT4o,
                maxAgentIterations = 100
            ),
            toolRegistry = ToolRegistry.EMPTY
        )

        val output = agent.run(researchBrief)

        assertTrue(output.contains("Result for Topic A"), "Output should include aggregated result for Topic A")
        assertTrue(output.contains("Result for Topic B"), "Output should include aggregated result for Topic B")
        assertTrue(output.contains("Consolidated Findings"), "Output should include consolidated findings section")
    }
}
