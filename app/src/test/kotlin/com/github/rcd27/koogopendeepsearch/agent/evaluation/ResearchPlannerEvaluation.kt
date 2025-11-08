package com.github.rcd27.koogopendeepsearch.agent.evaluation


import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import com.github.rcd27.koogopendeepsearch.DeepResearchAgent
import com.github.rcd27.koogopendeepsearch.agent.strategy.ResearchPlan
import com.github.rcd27.koogopendeepsearch.agent.strategy.ResearchQuestion
import com.github.rcd27.koogopendeepsearch.agent.strategy.nodeResearchPlanner
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

fun standaloneDecompositionStrategy(conversationPrompt: Prompt) =
    strategy<String, ResearchPlan>("strategy_with_research_decomposition") {
        val emulateChatHistory by node<String, String>("emulate_message_history") {
            llm.writeSession {
                prompt = conversationPrompt
            }
            "<bypass/>"
        }
        val nodeResearchPlanner: AIAgentNodeBase<ResearchQuestion, ResearchPlan> by nodeResearchPlanner()
        nodeStart then emulateChatHistory
        edge(
            emulateChatHistory forwardTo nodeResearchPlanner
                transformed { ResearchQuestion("") }
        )
        nodeResearchPlanner then nodeFinish
    }

class ResearchPlannerEvaluation {

    @Test
    fun `evaluate decomposition_conversation_1`(): Unit = runBlocking {
        val agent = DeepResearchAgent.withStrategy(standaloneDecompositionStrategy(conversation1))
        agent.evaluateWithRubric(
            input = "",
            criteria = criteria1,
            criterionPromptBuilder = ::decompositionCriteriaPrompt,
            messageHistory = conversation1
        ) { score: Double ->
            assert(score > 0.8)
        }
    }

    @Test
    fun `evaluate decomposition_conversation_2`(): Unit = runBlocking {
        val agent = DeepResearchAgent.withStrategy(standaloneDecompositionStrategy(conversation2))
        agent.evaluateWithRubric(
            input = "",
            criteria = criteria2,
            criterionPromptBuilder = ::decompositionCriteriaPrompt,
            messageHistory = conversation2
        ) { score: Double ->
            assert(score > 0.8)
        }
    }

    private companion object {
        //region dataset
        val conversation1 = prompt("conversation_1") {
            user("Research Brief: Investigate the impact of electric vehicles (EVs) on urban air quality, infrastructure, and energy demand.")
        }

        val criteria1 = listOf(
            "One component focuses on EVs' impact on air pollution reduction in urban areas.",
            "One component analyzes infrastructure challenges (charging stations, grid capacity, parking).",
            "One component explores effects on national or local energy demand.",
            "Each component is self-contained and can be researched independently.",
            "Components are non-overlapping but collectively cover the main research brief."
        )

        val conversation2 = prompt("conversation_2") {
            user("Research Brief: Study the influence of social media on adolescent mental health, including emotional wellbeing, social behavior, and sleep patterns.")
        }

        val criteria2 = listOf(
            "One component addresses emotional wellbeing and self-esteem impact from social media.",
            "One component studies changes in social behavior or peer interaction patterns.",
            "One component explores the effect of social media usage on sleep quality.",
            "Each component includes a short rationale explaining its independence and relevance.",
            "Decomposition output follows JSON structure with 3–5 elements."
        )
        //endregion

        fun decompositionCriteriaPrompt(input: EvaluationInput<ResearchPlan>) = """
<role>
You are an expert evaluator of research decomposition quality. 
Your task is to verify whether the generated research components accurately reflect and fully decompose the given Research Brief.
</role>

<task>
Evaluate whether the decomposition captures the specified success criterion. 
Return a binary judgment (CAPTURED / NOT CAPTURED) with detailed reasoning.
</task>

<evaluation_context>
Research decomposition is crucial for enabling parallel and specialized investigations. 
Each subtopic must be distinct, relevant, and logically derived from the main brief. 
Partial or overlapping coverage lowers research clarity and effectiveness.
</evaluation_context>

<criterion_to_evaluate>
${input.criterion}
</criterion_to_evaluate>

<decomposition_output>
${input.targetToJudge}
</decomposition_output>

<evaluation_guidelines>
CAPTURED if:
- The output explicitly addresses or clearly includes the criterion.
- The criterion’s intent is represented even if phrased differently.
- All key aspects of the criterion are covered in at least one research component.
- The structure aligns with the required 3–5 non-overlapping components.

NOT CAPTURED if:
- The criterion is missing or only vaguely implied.
- There is overlap or lack of independence among components.
- The output deviates from the required structured format or rationale requirements.
- The criterion is contradicted or insufficiently represented.
</evaluation_guidelines>

<evaluation_examples>
Example 1 — CAPTURED:
Criterion: "One component studies energy demand impact."
Output: "Component 3 — Energy Demand Dynamics: Examines how increased EV adoption affects citywide electricity consumption patterns."
Judgment: CAPTURED — topic and rationale align with criterion.

Example 2 — NOT CAPTURED:
Criterion: "Each component includes a rationale."
Output: "List of topics only, without explanations."
Judgment: NOT CAPTURED — rationale missing.

Example 3 — CAPTURED:
Criterion: "Components are non-overlapping."
Output: "Air quality, infrastructure, energy demand — each distinct."
Judgment: CAPTURED — topics clearly separated.
</evaluation_examples>

<output_instructions>
1. Examine the decomposition carefully for each criterion.
2. Provide quotes or clear references as evidence.
3. Be conservative — when unsure, mark as NOT CAPTURED.
4. Focus on whether other researchers could independently act on each component.
</output_instructions>
        """.trimIndent()
    }
}
