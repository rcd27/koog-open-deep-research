package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentGraphContext
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.FinishNode
import ai.koog.agents.core.agent.entity.StartNode
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import com.github.rcd27.koogopendeepsearch.testClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.reflect.typeOf
import kotlin.test.assertEquals

class NodeClarifyTest {

    @Test
    fun `Check system prompt is the same`() = runBlocking {
        val initialPrompt = prompt("initial_system_prompt") {
            user("You are advanced deep research agent")
        }

        val mockPromptExecutor = mockk<PromptExecutor>()

        val mockEnv = mockk<AIAgentEnvironment>()

        val initialModel = OllamaModels.Meta.LLAMA_3_2

        val mockLLM = AIAgentLLMContext(
            tools = emptyList(),
            toolRegistry = ToolRegistry.EMPTY,
            prompt = initialPrompt,
            model = initialModel,
            promptExecutor = mockPromptExecutor,
            environment = mockEnv,
            config = AIAgentConfig(
                prompt = initialPrompt,
                model = OpenAIModels.CostOptimized.GPT4oMini,
                maxAgentIterations = 10
            ),
            clock = testClock
        )

        val stateManager = mockk<AIAgentStateManager>()

        val context = AIAgentGraphContext(
            environment = mockEnv,
            agentId = "test-agent",
            agentInputType = typeOf<String>(),
            agentInput = "Please make a deep research about my Honda",
            config = mockk(),
            llm = mockLLM,
            stateManager = stateManager,
            storage = mockk(),
            runId = "run-1",
            strategyName = "test-strategy",
            pipeline = AIAgentGraphPipeline(),
        )

        val subgraphContext = object : AIAgentSubgraphBuilderBase<String, String>() {
            override val nodeStart: StartNode<String> = mockk()
            override val nodeFinish: FinishNode<String> = mockk()
        }

        val nodeClarify by subgraphContext.nodeClarify()

        coEvery { mockPromptExecutor.execute(any(), any(), any()) } returns listOf(
            Message.Assistant(
                content = Json.encodeToString(
                    ClarifyWithUser.serializer(),
                    ClarifyWithUser(
                        needClarification = true,
                        question = "What type of engine do you want to research?",
                        verification = ""
                    )
                ),
                metaInfo = ResponseMetaInfo.create(testClock),
            )
        )

        nodeClarify.execute(context, input = (unitStub()))

        coVerify {
            mockPromptExecutor.execute(
                prompt = match { (it.messages.size == 1) && (it.id == "clarify_with_user_instructions_prompt") },
                model = match { it == initialModel },
                tools = any()
            )
        }

        assertEquals(initialPrompt, context.llm.prompt)
    }
}

fun unitStub() {
    return
}
