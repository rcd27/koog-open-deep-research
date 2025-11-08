package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.structure.StructureFixingParser
import com.github.rcd27.koogopendeepsearch.DeepResearchAgent
import com.github.rcd27.koogopendeepsearch.agent.tools.thinkTool
import com.github.rcd27.koogopendeepsearch.standaloneResearchStrategy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable

fun leadResearcherPrompt(
    date: String,
    maxResearcherIterations: Int = 3,
    maxConcurrentResearchUnits: Int = 3
) = """
You are a research supervisor. Your job is to conduct research by calling the "ConductResearch" tool. 

For context, today's date is $date.

<Task>
Your focus is to call the "ConductResearch" tool to conduct research against the overall research question passed in by the user. 
When you are completely satisfied with the research findings returned from the tool calls, then you should call the "ResearchComplete" tool to indicate that you are done with your research.
</Task>

<Available Tools>
You have access to three main tools:
1. **ConductResearch**: Delegate research tasks to specialized sub-agents
2. **ResearchComplete**: Indicate that research is complete
3. **thinkTool**: For reflection and strategic planning during research

**CRITICAL: Use thinkTool before calling ConductResearch to plan your approach, and after each ConductResearch to assess progress. Do not call thinkTool with any other tools in parallel.**
</Available Tools>

<Instructions>
Think like a research manager with limited time and resources. Follow these steps:

1. **Read the question carefully** - What specific information does the user need?
2. **Decide how to delegate the research** - Carefully consider the question and decide how to delegate the research. Are there multiple independent directions that can be explored simultaneously?
3. **After each call to ConductResearch, pause and assess** - Do I have enough to answer? What's still missing?
</Instructions>

<Hard Limits>
**Task Delegation Budgets** (Prevent excessive delegation):
- **Bias towards single agent** - Use single agent for simplicity unless the user request has clear opportunity for parallelization
- **Stop when you can answer confidently** - Don't keep delegating research for perfection
- **Limit tool calls** - Always stop after $maxResearcherIterations tool calls to ConductResearch and thinkTool 
if you cannot find the right sources

**Maximum $maxConcurrentResearchUnits parallel agents per iteration**
</Hard Limits>

<Show Your Thinking>
Before you call ConductResearch tool call, use thinkTool to plan your approach:
- Can the task be broken down into smaller sub-tasks?

After each ConductResearch tool call, use thinkTool to analyze the results:
- What key information did I find?
- What's missing?
- Do I have enough to answer the question comprehensively?
- Should I delegate more research or call ResearchComplete?
</Show Your Thinking>

<Scaling Rules>
**Simple fact-finding, lists, and rankings** can use a single sub-agent:
- *Example*: List the top 10 coffee shops in San Francisco → Use 1 sub-agent

**Comparisons presented in the user request** can use a sub-agent for each element of the comparison:
- *Example*: Compare OpenAI vs. Anthropic vs. DeepMind approaches to AI safety → Use 3 sub-agents
- Delegate clear, distinct, non-overlapping subtopics

**Important Reminders:**
- Each ConductResearch call spawns a dedicated research agent for that specific topic
- A separate agent will write the final report - you just need to gather information
- When calling ConductResearch, provide complete standalone instructions - sub-agents can't see other agents' work
- Do NOT use acronyms or abbreviations in your research questions, be very clear and specific
</Scaling Rules>""".trimIndent()

@Serializable
sealed interface ResearchAction

@Serializable
data class ConductResearch<T>(
    @property:LLMDescription("Call this tool to conduct research on a specific topic.")
    val researchTopic: T
) : ResearchAction

@Serializable
@LLMDescription("Call this tool to indicate that the research is complete.")
object ResearchComplete : ResearchAction

//@Serializable
//@LLMDescription("Research summary with key findings.")
//data class Summary(
//    @property:LLMDescription("Research summary.")
//    val summary: String,
//    @property:LLMDescription("Key findings.")
//    val keyConcepts: String
//)

@Serializable
data class SupervisorIteration(
    val subtopics: List<String>,
    val findingsSummary: String
)

@Serializable
data class SupervisorState(
    val brief: String,
    val iterations: List<SupervisorIteration>
)

@Serializable
@LLMDescription("Plan your approach and assess progress.")
data class SupervisorPlan(
    @property:LLMDescription("The action to take next.")
    val action: ResearchAction,
    @property:LLMDescription("Subtopics that need to be research")
    val subtopics: List<String>,
    @property:LLMDescription("A summary of the findings so far.")
    val finalSummary: String
)

@Serializable
data class PlanOutcome(
    val state: SupervisorState,
    val plan: SupervisorPlan
)

/**
 *  Lead research supervisor that plans research strategy and delegates to researchers.
 *
 *  The supervisor analyzes the research brief and decides how to break down the research
 *  into manageable tasks. It can use thinkTool for strategic planning, ConductResearch
 *  to delegate tasks to sub-researchers, or ResearchComplete when satisfied with findings.
 */
fun AIAgentSubgraphBuilderBase<*, *>.nodeResearchSupervisor(): AIAgentNodeDelegate<ResearchQuestion, String> =
    // TODO: seems like agents should be inside this, the return type is Either<ConductResearch, ResearchComplete>
    node("research_supervisor") { input ->
        llm.writeSession {
            val initialPrompt = prompt
            prompt = prompt("research_supervisor_prompt") {
                system(leadResearcherPrompt(getTodayStr()))
                user(input.researchBrief)
            }
            prompt = initialPrompt
        }
        // TODO: split ResearchQuestion to different topics if it can be parallelized
        input.researchBrief
    }

fun AIAgentSubgraphBuilderBase<*, *>.subgraphResearchSupervisor(
    maxIterations: Int = 3,
    maxConcurrent: Int = 3
): AIAgentSubgraphDelegate<ResearchQuestion, String> = subgraph("research_supervisor") {

    val initState by node<ResearchQuestion, SupervisorState>("init_state") { brief ->
        SupervisorState(brief = brief.researchBrief, iterations = emptyList())
    }

    val planNode by node<SupervisorState, PlanOutcome>("supervisor_plan") { state ->
        // Enforce hard limit before asking LLM
        if (state.iterations.size >= maxIterations) {
            return@node PlanOutcome(
                state = state,
                plan = SupervisorPlan(
                    action = ResearchComplete,
                    finalSummary = "Iteration budget exhausted. Proceeding to finalize based on gathered findings.",
                    subtopics = emptyList()
                )
            )
        }

        val historyText = buildString {
            state.iterations.forEachIndexed { idx, it ->
                appendLine("Iteration #${idx + 1}:")
                appendLine("- Subtopics: ${it.subtopics.joinToString()}")
                appendLine("- Findings summary: ${it.findingsSummary.take(1000)}")
                appendLine()
            }
        }

        val decision = llm.writeSession {
            val initialPrompt = prompt
            prompt = prompt("research_supervisor_prompt") {
                system(leadResearcherPrompt(getTodayStr(), maxIterations, maxConcurrent))
                user(
                    buildString {
                        appendLine("Research brief:")
                        appendLine(state.brief)
                        appendLine()
                        appendLine("History:")
                        appendLine(historyText.ifBlank { "<empty>" })
                    }
                )
            }

            val structured = requestLLMStructured<SupervisorPlan>(
                examples = listOf(
                    SupervisorPlan(
                        action = ConductResearch("Research Topic A"),
                        finalSummary = "We have sufficient findings.",
                        subtopics = listOf(
                            "Subtopic A", "Subtopic B"
                        )
                    ),
                    SupervisorPlan(
                        action = ResearchComplete,
                        finalSummary = "We have sufficient findings.",
                        subtopics = listOf("Subtopic A", "Subtopic B")
                    )
                ),
                fixingParser = StructureFixingParser(
                    fixingModel = OpenAIModels.CostOptimized.GPT4oMini,
                    retries = 3
                )
            ).getOrThrow().structure

            prompt = initialPrompt
            structured
        }

        PlanOutcome(state = state, plan = decision)
    }

    val executeNode by node<PlanOutcome, SupervisorState>("supervisor_execute") { outcome ->
        val state = outcome.state
        val plan = outcome.plan
        val subtopics = plan.subtopics.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (subtopics.isEmpty()) {
            return@node state
        }

        val sem = Semaphore(maxConcurrent)
        val results: List<String> = coroutineScope {
            subtopics.map { topic ->
                async {
                    sem.withPermit {
                        val researcherAgent = DeepResearchAgent.withStrategy(
                            standaloneResearchStrategy(
                                prompt("researcher") {
                                    system(topic)
                                }
                            ))
                        researcherAgent.run(state.brief)
                    }
                }
            }.awaitAll()
        }

        val findingsSummary = results.joinToString(separator = "\n\n") { it }
        val updatedIter = SupervisorIteration(
            subtopics = subtopics,
            findingsSummary = findingsSummary
        )
        state.copy(iterations = state.iterations + updatedIter)
    }

    val reflectNode by node<SupervisorState, SupervisorState>("supervisor_reflect") { state ->
        // Produce a brief reflection via LLM, then record via thinkTool
        val reflection = llm.writeSession {
            val initialPrompt = prompt
            prompt = prompt("supervisor_reflection") {
                system("Reflect on the current research progress and plan the next step. Keep it brief (<= 5 sentences).")
                user(
                    buildString {
                        appendLine("Brief:")
                        appendLine(state.brief)
                        appendLine()
                        appendLine("Latest findings summary:")
                        appendLine(state.iterations.lastOrNull()?.findingsSummary?.take(1000) ?: "<none>")
                    }
                )
            }
            val resp = requestLLM().content
            prompt = initialPrompt
            resp
        }
        // Record reflection via thinkTool (not in parallel with ConductResearch)
        thinkTool(reflection)
        state
    }

    val finishNode by node<PlanOutcome, String>("supervisor_finish") { outcome ->
        val state = outcome.state
        val plan = outcome.plan
        val final = buildString {
            if (plan.finalSummary.isNotBlank()) {
                appendLine(plan.finalSummary)
                appendLine()
            }
            appendLine("--- Consolidated Findings ---")
            state.iterations.forEachIndexed { idx, it ->
                appendLine("Iteration #${idx + 1}")
                appendLine("Subtopics: ${it.subtopics.joinToString()}")
                appendLine(it.findingsSummary)
                appendLine()
            }
        }.trim()
        final
    }

    // Graph wiring
    edge(nodeStart forwardTo initState)
    edge(initState forwardTo planNode)

    // If ConductResearch -> execute -> reflect -> plan (loop)
    edge(
        planNode forwardTo executeNode
            onCondition { it.plan.action is ConductResearch<*> }
    )
    edge(executeNode forwardTo reflectNode)
    edge(reflectNode forwardTo planNode)

    // If ResearchComplete -> finish
    edge(
        planNode forwardTo finishNode
            onCondition { it.plan.action is ResearchComplete }
    )

    edge(finishNode forwardTo nodeFinish)
}
