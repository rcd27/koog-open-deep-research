package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import kotlinx.serialization.Serializable

fun leadResearcherPrompt(date: String, max_concurrent_research_units: Int = 3) =
    """You are a research supervisor. Your job is to conduct research by calling the "ConductResearch" tool. 

For context, today's date is $date.

<Task>
Your focus is to call the "ConductResearch" tool to conduct research against the overall research question passed in by the user. 
When you are completely satisfied with the research findings returned from the tool calls, then you should call the "ResearchComplete" tool to indicate that you are done with your research.
</Task>

<Available Tools>
You have access to three main tools:
1. **ConductResearch**: Delegate research tasks to specialized sub-agents
2. **ResearchComplete**: Indicate that research is complete
3. **think_tool**: For reflection and strategic planning during research

**CRITICAL: Use think_tool before calling ConductResearch to plan your approach, and after each ConductResearch to assess progress. Do not call think_tool with any other tools in parallel.**
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
- **Limit tool calls** - Always stop after {max_researcher_iterations} tool calls to ConductResearch and think_tool if you cannot find the right sources

**Maximum ${max_concurrent_research_units} parallel agents per iteration**
</Hard Limits>

<Show Your Thinking>
Before you call ConductResearch tool call, use think_tool to plan your approach:
- Can the task be broken down into smaller sub-tasks?

After each ConductResearch tool call, use think_tool to analyze the results:
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
</Scaling Rules>
    """.trimIndent()

@Serializable
data class ConductResearch<T>(
    @property:LLMDescription("Call this tool to conduct research on a specific topic.")
    val researchTopic: T
)

@Serializable
@LLMDescription("Call this tool to indicate that the research is complete.")
object ResearchComplete

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
 *
 *  The supervisor analyzes the research brief and decides how to break down the research
 *  into manageable tasks. It can use think_tool for strategic planning, ConductResearch
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
