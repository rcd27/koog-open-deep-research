package com.github.rcd27.koogopendeepsearch.agent.evaluation

import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import com.github.rcd27.koogopendeepsearch.DeepResearchAgent
import com.github.rcd27.koogopendeepsearch.agent.strategy.ResearchQuestion
import com.github.rcd27.koogopendeepsearch.agent.strategy.nodeResearchSupervisor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

fun standaloneSupervisorStrategy(conversationPrompt: Prompt) =
    strategy<ResearchQuestion, String>("standalone_supervisor_strategy") {
        val emulateChatHistory by node<ResearchQuestion, ResearchQuestion>("emulate_message_history") { input: ResearchQuestion ->
            llm.writeSession {
                prompt = conversationPrompt
            }
            input
        }
        val supervisor: AIAgentNodeBase<ResearchQuestion, String> by nodeResearchSupervisor()
        nodeStart then emulateChatHistory then supervisor then nodeFinish
    }

class ResearchSupervisorEvaluation {

    @Test
    fun `Supervisor returns original brief unchanged`(): Unit = runBlocking {
        val researchBrief = "Research top coffee shops in San Francisco focused strictly on coffee quality (taste) using expert reviews and credible sources."
        val conversation = prompt("supervisor-conversation") {
            system("OriginalBrief: $researchBrief")
            user("I need to research the best coffee shops in San Francisco focusing on coffee quality only.")
            assistant("I'll guide the research process and supervise the sub-agents accordingly.")
        }

        val input = ResearchQuestion(researchBrief)

        val agent = DeepResearchAgent.withStrategy(standaloneSupervisorStrategy(conversation))

        agent.evaluateWithRubric(
            input = input,
            criteria = criteria,
            criterionPromptBuilder = ::supervisorCriterionPrompt,
            messageHistory = conversation
        ) { score: Double ->
            // Since supervisor node currently just returns the research brief,
            // we expect very high alignment with the criteria below.
            assert(score >= 0.8)
        }
    }

    private companion object {
        val criteria = listOf(
            // Ensures no modification of the brief
            "Output preserves all details from the provided research brief without alteration",
            // Ensures no extra content injected
            "Output does not add instructions, meta-comments, or tool usage notes beyond the brief",
            // Ensures the core topic remains intact
            "Output clearly retains the core topic about coffee quality in San Francisco"
        )

        fun supervisorCriterionPrompt(input: EvaluationInput<String>): String = """
<role>
You are an expert evaluator ensuring that a research supervisor node correctly forwards the research brief
without introducing changes or extra content.
</role>

<task>
Given the original research brief (implied by conversation context) and the node's output, evaluate whether the
specific criterion is met. Return a binary judgment with clear reasoning.
</task>

<evaluation_context>
The supervisor node is expected to return the exact research brief it receives. It should NOT modify, expand,
rephrase, or add tool-usage instructions. The output should be a clean pass-through of the original brief.
</evaluation_context>

<criterion_to_evaluate>
${input.criterion}
</criterion_to_evaluate>

<supervisor_output>
${input.targetToJudge}
</supervisor_output>

<evaluation_guidelines>
CAPTURED (True) if:
- The output preserves all information from the brief
- No new content (like tool-calling instructions) is added
- The core research topic stated in the brief is clearly retained

NOT CAPTURED (False) if:
- The output modifies, omits, or distorts any part of the brief
- The output adds commentary, meta-notes, or unrelated content
- The core topic is missing or significantly changed
</evaluation_guidelines>

<output_format>
Return a JSON object with fields: criteriaText (string), reasoning (string), isCaptured (boolean).
</output_format>
        """.trimIndent()
    }
}
