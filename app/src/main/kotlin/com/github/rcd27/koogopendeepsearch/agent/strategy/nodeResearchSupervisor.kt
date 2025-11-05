package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import kotlinx.serialization.Serializable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import ai.koog.agents.core.dsl.builder.strategy
import com.github.rcd27.koogopendeepsearch.DeepResearchAgent

fun leadResearcherPrompt(
    date: String,
    maxResearcherIterations: Int = 3,
    maxConcurrentResearchUnits: Int = 3
) ="""
You are a research supervisor. Your job is to coordinate specialized researchers by calling the "ConductResearch" tool.

For context, today's date is $date.

<Task>
Your focus is to plan and delegate research against the overall research question passed in by the user.
When you are completely satisfied with the research findings returned from the delegated research, then set done=true in your decision.
</Task>

<Available Tools>
Conceptual tools for your internal planning loop (the host runtime will execute them for you):
1. **ConductResearch(topics: List<String>)**: Delegate research tasks to specialized sub-agents (one per topic)
2. **ResearchComplete**: Indicate that research is complete (use done=true)
3. **think_tool**: For reflection and strategic planning during research

**CRITICAL: Use think_tool before calling ConductResearch to plan your approach, and after each ConductResearch to assess progress. Do not call think_tool with any other tools in parallel.**
</Available Tools>

<Instructions>
Think like a research manager with limited time and resources. Follow these steps:

1. **Read the question carefully** - What specific information does the user need?
2. **Decide how to delegate the research** - Carefully consider the question and decide how to delegate the research. Are there multiple independent directions that can be explored simultaneously?
3. **After each delegation, pause and assess** - Do I have enough to answer? What's still missing?
</Instructions>

<Hard Limits>
**Task Delegation Budgets** (Prevent excessive delegation):
- **Bias towards single agent** - Use single agent for simplicity unless the user request has clear opportunity for parallelization
- **Stop when you can answer confidently** - Don't keep delegating research for perfection
- **Limit iterations** - Always stop after $maxResearcherIterations planning iterations if you cannot find the right sources

**Maximum $maxConcurrentResearchUnits parallel agents per iteration**
</Hard Limits>

<Show Your Thinking>
Before you decide to delegate, use think_tool to plan your approach:
- Can the task be broken down into smaller sub-tasks? If yes, produce 2-4 clear, non-overlapping topics.

After each delegation, use think_tool to analyze the results:
- What key information did I find?
- What's missing?
- Do I have enough to answer the question comprehensively?
- Should I delegate more or mark done?
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
- When delegating, provide complete standalone topic questions - sub-agents can't see other agents' work
- Do NOT use acronyms or abbreviations in your research questions, be very clear and specific
</Scaling Rules>

<Output>
Return a strict JSON object matching this Kotlin schema:
- done: Boolean (true when research is complete)
- topics: List<String> (0..$maxConcurrentResearchUnits topics to research in this iteration; empty list if done=true)
- rationale: String (why these topics; or why done)
</Output>
""".trimIndent()

@Serializable
data class DelegationDecision(
    @property:LLMDescription("Set true when research is complete for the user's brief.")
    val done: Boolean,
    @property:LLMDescription("Up to N parallel topics to delegate in this iteration.")
    val topics: List<String> = emptyList(),
    @property:LLMDescription("Short rationale explaining the decision and topic choices.")
    val rationale: String = ""
)

@Serializable
@LLMDescription("Research summary with key findings.")
data class Summary(
    @property:LLMDescription("Research summary.")
    val summary: String,
    @property:LLMDescription("Key findings.")
    val keyConcepts: String
)

/**
 *  Lead research supervisor that plans research strategy and delegates to researchers.
 */
fun AIAgentSubgraphBuilderBase<*, *>.nodeResearchSupervisor(): AIAgentNodeDelegate<ResearchQuestion, String> =
    node("research_supervisor") { input ->
        val maxIterations = 3
        val maxParallel = 3

        // Plan and delegate in iterations
        val collected = mutableListOf<Pair<String, String>>() // (topic to report)

        llm.writeSession {
            val initialPrompt = prompt.copy()
            prompt = prompt("research_supervisor_prompt") {
                system(leadResearcherPrompt(getTodayStr(), maxIterations, maxParallel))
                user(input.researchBrief)
            }

            repeat(maxIterations) { _ ->
                // Ask for decision
                val decision: DelegationDecision = requestLLMStructured<DelegationDecision>()
                    .getOrThrow().structure

                if (decision.done || decision.topics.isEmpty()) {
                    return@writeSession run {
                        prompt = initialPrompt
                        // Aggregate and return
                        buildString {
                            appendLine("Supervisor Decision: DONE")
                            appendLine()
                            if (collected.isNotEmpty()) {
                                appendLine("# Aggregated Research Findings")
                                collected.forEachIndexed { idx, (topic, report) ->
                                    appendLine("## Topic ${idx + 1}: $topic")
                                    appendLine(report)
                                    appendLine()
                                }
                            } else {
                                appendLine(input.researchBrief)
                            }
                        }
                    }
                }

                // Run up to maxParallel researchers for the proposed topics
                val batch = decision.topics.take(maxParallel)

                val reports: List<Pair<String, String>> = coroutineScope {
                    batch.map { topic ->
                        async {
                            val strategy = strategy<String, String>("supervisor_spawned_researcher") {
                                val researcher by subgraphResearcher()
                                nodeStart.then(researcher).then(nodeFinish)
                            }
                            val agent = DeepResearchAgent.withStrategy(strategy)
                            val result = agent.run(topic)
                            topic to result
                        }
                    }.awaitAll()
                }
                collected.addAll(reports)

                // After each delegation iteration, append a reflection note to the session (simulating think_tool)
                appendPrompt {
                    system("Reflection: Delegated ${reports.size} topic(s) -> ${batch.joinToString()}. Continue if gaps remain.")
                }
            }

            // Budget exhausted; return what we have
            val result = buildString {
                appendLine("Supervisor Decision: BUDGET_EXHAUSTED")
                appendLine()
                appendLine("# Aggregated Research Findings")
                collected.forEachIndexed { idx, (topic, report) ->
                    appendLine("## Topic ${idx + 1}: $topic")
                    appendLine(report)
                    appendLine()
                }
            }

            prompt = initialPrompt
            result
        }
    }
