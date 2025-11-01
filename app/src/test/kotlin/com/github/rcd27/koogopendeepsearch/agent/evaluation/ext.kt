package com.github.rcd27.koogopendeepsearch.agent.evaluation

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.structure.StructureFixingParser
import com.github.rcd27.koogopendeepsearch.agent.executor.openAISinglePromptExecutor
import com.github.rcd27.koogopendeepsearch.agent.utils.foldPromptMessages
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

data class EvaluationInput<T>(
    val criterion: String,
    val targetToJudge: T
)

@LLMDescription(
    "    Individual success criteria evaluation result.\n" +
        "    \n" +
        "    This model represents a single evaluation criteria that should be present\n" +
        "    in the research brief, along with a detailed assessment of whether it was\n" +
        "    successfully captured and the reasoning behind that assessment."
)
@Serializable
data class Criteria(
    @property:LLMDescription("The specific success criteria being evaluated (e.g., 'Current age is 25', 'Monthly rent below 7k')")
    val criteriaText: String,
    @property:LLMDescription("Detailed explanation of why this criteria is or isn't captured in the research brief, including specific evidence from the brief")
    val reasoning: String,
    @property:LLMDescription("Whether this specific criteria is adequately captured in the research brief (True) or missing/inadequately addressed (False)")
    val isCaptured: Boolean
)

/**
 * LLM judge that evaluates whether research briefs capture specific criteria.
 */
fun evaluateSuccessCriteriaStrategy(llmRubricPrompt: String, messageHistory: Prompt) =
    strategy<String, Criteria>("research_brief_evaluation") {
        /* This is basically copy-paste from LLMAsJudge but with Criteria output */
        val judge by node<String, Criteria>("judge") { _: String ->
            llm.writeSession {
                val initialPrompt = prompt.copy()
                val initialModel = model.copy()

                prompt = prompt("critic") {
                    val combinedMessage = messageHistory.messages.foldPromptMessages()
                    system(llmRubricPrompt)
                    user(combinedMessage)
                }

                val result = requestLLMStructured<Criteria>(
                    examples = emptyList(),
                    fixingParser = StructureFixingParser(
                        fixingModel = OpenAIModels.CostOptimized.GPT4oMini,
                        retries = 3,
                    )
                ).getOrThrow().structure

                prompt = initialPrompt
                model = initialModel
                result
            }
        }

        nodeStart then judge then nodeFinish
    }

fun <INPUT, OUTPUT> AIAgent<INPUT, OUTPUT>.evaluateWithRubric(
    input: INPUT,
    criteria: List<String>,
    criterionPromptBuilder: (EvaluationInput<OUTPUT>) -> String,
    messageHistory: Prompt = Prompt.Empty,
    scoring: (Double) -> Unit
) = runBlocking {
    val outputToEvaluate = this@evaluateWithRubric.run(input)
    // TODO: mapParallel
    val capturedCount = criteria.map { criterion ->
        // TODO: move to function config
        val evaluationAgentConfig = AIAgentConfig.withSystemPrompt(
            prompt = "EMPTY",
            maxAgentIterations = 50,
            llm = OpenAIModels.Chat.GPT4o // <<< GPT4o for judging llm-rubric
        )
        val evaluationInput = EvaluationInput(criterion, outputToEvaluate)
        val criterionPrompt = criterionPromptBuilder(evaluationInput)
        val evaluationAgent = AIAgent(
            promptExecutor = openAISinglePromptExecutor,
            strategy = evaluateSuccessCriteriaStrategy(criterionPrompt, messageHistory),
            agentConfig = evaluationAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        )
        val result: Criteria = evaluationAgent.run("STUB")
        result
    }
        .fold(0) { acc, i ->
            if (i.isCaptured) acc + 1 else acc
        }

    val score: Double = capturedCount.toDouble() / criteria.size
    scoring.invoke(score)
}
