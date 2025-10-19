package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import com.github.rcd27.koogopendeepsearch.agent.utils.foldPromptMessages
import kotlinx.serialization.Serializable

fun transformMessagesIntoResearchTopicPrompt(messages: String, date: String): String = """
You will be given a set of messages that have been exchanged so far between yourself and the user.
Your job is to translate these messages into a more detailed and concrete research question that will be used to guide the research.

The messages that have been exchanged so far between yourself and the user are:
<Messages>
$messages
</Messages>

Today's date is ${date}.

You will return a single research question that will be used to guide the research.

Guidelines:
1. Maximize Specificity and Detail
- Include all known user preferences and explicitly list key attributes or dimensions to consider.
- It is important that all details from the user are included in the instructions.

2. Handle Unstated Dimensions Carefully
- When research quality requires considering additional dimensions that the user hasn't specified, acknowledge them as open considerations rather than assumed preferences.
- Example: Instead of assuming "budget-friendly options," say "consider all price ranges unless cost constraints are specified."
- Only mention dimensions that are genuinely necessary for comprehensive research in that domain.

3. Avoid Unwarranted Assumptions
- Never invent specific user preferences, constraints, or requirements that weren't stated.
- If the user hasn't provided a particular detail, explicitly note this lack of specification.
- Guide the researcher to treat unspecified aspects as flexible rather than making assumptions.

4. Distinguish Between Research Scope and User Preferences
- Research scope: What topics/dimensions should be investigated (can be broader than user's explicit mentions)
- User preferences: Specific constraints, requirements, or preferences (must only include what user stated)
- Example: "Research coffee quality factors (including bean sourcing, roasting methods, brewing techniques) for San Francisco coffee shops, with primary focus on taste as specified by the user."

5. Use the First Person
- Phrase the request from the perspective of the user.

6. Sources
- If specific sources should be prioritized, specify them in the research question.
- For product and travel research, prefer linking directly to official or primary websites (e.g., official brand sites, manufacturer pages, or reputable e-commerce platforms like Amazon for user reviews) rather than aggregator sites or SEO-heavy blogs.
- For academic or scientific queries, prefer linking directly to the original paper or official journal publication rather than survey papers or secondary summaries.
- For people, try linking directly to their LinkedIn profile, or their personal website if they have one.
- If the query is in a specific language, prioritize sources published in that language.
""".trimIndent()

@Serializable
@LLMDescription("Schema for structured research brief generation.")
data class ResearchQuestion(
    @property:LLMDescription("A research question that will be used to guide the research.")
    val researchBrief: String
)

fun AIAgentSubgraphBuilderBase<*, *>.nodeWriteResearchBrief(): AIAgentNodeDelegate<String, ResearchQuestion> =
    node("write_research_brief") { nodeInput ->
        llm.writeSession {
            val initialPrompt = prompt.copy()
            prompt = prompt("write_research_brief_prompt") {
                system(
                    transformMessagesIntoResearchTopicPrompt(
                        messages = initialPrompt.messages.foldPromptMessages(),
                        date = getTodayStr()
                    )
                )
            }
            val result: ResearchQuestion = requestLLMStructured<ResearchQuestion>().getOrThrow().structure
            prompt = initialPrompt
            result
        }
    }