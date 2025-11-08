package com.github.rcd27.koogopendeepsearch.agent.evaluation

import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import com.github.rcd27.koogopendeepsearch.DeepResearchAgent
import com.github.rcd27.koogopendeepsearch.agent.strategy.standaloneResearchStrategy
import com.github.rcd27.koogopendeepsearch.agent.strategy.subgraphResearcher
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

val messagesShouldContinue = prompt("agent-should-continue") {
    user("What are the top coffee shops in San Francisco based on coffee quality?")
    assistant("I'll search for information about top coffee shops in San Francisco focusing on coffee quality.")
    tool {
        call(
            id = "call_continue_search_001",
            content = """
                {"query": "coffee shops San Francisco popular locations"}
            """.trimIndent(),
            tool = "tavily-search"
        )
        result(
            id = "call_continue_search_001",
            tool = "tavily-search",
            content = """
                {"result": "Search results show popular San Francisco cafes: Blue Bottle Coffee has great wifi and 
                ambiance, Philz Coffee is known for custom blends and friendly service, Café Reveille has multiple 
                locations with good pastries and laptop-friendly spaces. Ritual Coffee offers cozy seating and is popular with students."},
            """.trimIndent()
        )
    }
    assistant("Let me use the thinkTool to analyze these search results and determine next steps.")
    tool {
        call(
            id = "call_continue_search_002",
            content = """
                {"args": {"reflection": "Analysis of search results: The information I found focuses on general cafe 
                features like wifi, ambiance, custom blends, and seating - but lacks specific coffee quality metrics.
                 The user specifically asked for evaluation based on coffee quality, which would require expert reviews, professional ratings, specialty certifications, or quality-focused customer feedback. I need to search specifically for coffee quality assessments, Coffee Review scores, specialty coffee credentials, or expert evaluations of SF coffee shops. The current information is insufficient to answer the user's question about coffee quality."}}
            """.trimIndent(),
            tool = "thinkTool"
        )
        result(
            id = "call_continue_search_002",
            content = """
                {"result": "Reflection recorded: Analysis of search results: The information I found focuses on general cafe features like wifi, ambiance, custom blends, and seating - but lacks specific coffee quality metrics. The user specifically asked for evaluation based on coffee quality, which would require expert reviews, professional ratings, specialty certifications, or quality-focused customer feedback. I need to search specifically for coffee quality assessments, Coffee Review scores, specialty coffee credentials, or expert evaluations of SF coffee shops. The current information is insufficient to answer the user's question about coffee quality."}
            """.trimIndent(),
            tool = "thinkTool"
        )
    }
}

val messagesShouldStop = prompt("agent-should-stop") {
    user("What are the top coffee shops in San Francisco based on coffee quality?")
    assistant("I'll research San Francisco coffee shops specifically focusing on coffee quality assessments and expert reviews.")
    tool {
        call(
            id = "call_continue_search_001",
            content = """
                {"query": "best coffee quality San Francisco specialty coffee expert reviews Coffee Review ratings 2024"}
            """.trimIndent(),
            tool = "tavily-search"
        )
        // This ToolMessage provides COMPREHENSIVE coffee quality information - sufficient to answer the question
        result(
            id = "call_continue_search_001",
            content = """
                Coffee quality research for San Francisco reveals top establishments based on expert reviews and specialty coffee credentials:

                **Top Quality-Focused Coffee Shops:**
                1. **Sightglass Coffee** - Coffee Review score 94/100, known for single-origin beans and precise roasting techniques. Featured in Bon Appétit's "Best Coffee in America" list for exceptional coffee quality.

                2. **Saint Frank Coffee** - Ranked #3 coffee shop in America by The Daily Meal based on coffee quality. 4.3/5 Yelp rating with reviews specifically praising "exceptional bean quality" and "perfect extraction."

                3. **Ritual Coffee Roasters** - Specialty Coffee Association member, direct trade relationships, consistently rated 4.4/5 on Google with reviews highlighting "superior coffee taste" and "expert barista skills."

                4. **Blue Bottle Coffee** - Third-wave coffee pioneer, Coffee Review scores averaging 90+/100, known for rigorous quality control and fresh roasting practices.

                5. **Four Barrel Coffee** - Local roaster with Coffee Review scores 88-92/100, praised by industry experts for "exceptional coffee quality" and innovative brewing methods.

                **Quality Indicators Found:**
                - Coffee Review professional scores (88-94/100 range)
                - Specialty Coffee Association certifications
                - Direct trade/single-origin sourcing
                - Expert recognition from food publications
                - Customer reviews specifically mentioning coffee taste and quality

                This provides a comprehensive assessment of SF coffee shops based specifically on coffee quality rather than ambiance or service.
            """.trimIndent(),
            tool = "tavily-search"
        )
    }
    assistant("Let me reflect on the search results using the thinkTool to assess whether I have sufficient information.")
    tool {
        call(
            id = "call_continue_search_002",
            content = """
                {"args": {"reflection": "Analysis of search results: The information I found focuses on general cafe 
                features like wifi, ambiance, custom blends, and seating - but lacks specific coffee quality metrics."}}
            """,
            tool = "thinkTool"
        )
        result(
            id = "call_continue_search_002",
            content = """
                {"result": "Reflection recorded: Analysis of search results: The information I found focuses on general cafe features like wifi, ambiance, custom blends, and seating - but lacks specific coffee quality metrics."}
            """.trimIndent(),
            tool = "thinkTool"
        )
    }
}

class ResearcherEvaluation {

    @Test
    fun `Should continue research`(): Unit = runBlocking {
        val researcherAgent = DeepResearchAgent.withStrategy(standaloneResearchStrategy(messagesShouldContinue))
        researcherAgent.evaluateWithRubric(
            input = "",
            criteria = criteria1,
            criterionPromptBuilder = ::researcherCriterionPrompt,
            messageHistory = messagesShouldContinue
        ) { score: Double ->
            assert(score < 0.8) // we treat this scoring as bad research
        }
    }

    @Test
    fun `Should stop research`(): Unit = runBlocking {
        val researcherAgent = DeepResearchAgent.withStrategy(standaloneResearchStrategy(messagesShouldStop))
        researcherAgent.evaluateWithRubric(
            input = "",
            criteria = criteria1,
            criterionPromptBuilder = ::researcherCriterionPrompt,
            messageHistory = messagesShouldStop
        ) { score: Double ->
            assert(score >= 0.8) // 80%+ is good
        }
    }

    private companion object {
        val criteria1 = listOf(
            "Search Strategy: Uses appropriate number of searches (2-5), progresses from broad to narrow queries, and avoids redundancy",
            "Reflection Discipline: Consistently uses thinkTool after each search (not in parallel) to analyze results and plan next steps",
            "Answer Quality: Gathers sufficient relevant sources (3+) and stops when question can be answered comprehensively",
            "Tool Compliance: Properly uses tavily-search for searches, thinkTool only for reflection, and respects all tool-calling constraints",
            "Research Judgement: Demonstrates when to stop—balances thoroughness with efficiency, avoiding both premature stopping and over-researching"
        )

        fun researcherCriterionPrompt(input: EvaluationInput<String>): String = """
<role>
You are an expert research agent evaluator specializing in assessing the quality, efficiency, and effectiveness 
of AI agents conducting information gathering tasks. You evaluate based on concrete evidence from tool usage 
patterns and research outcomes.
</role>

<task>
Evaluate the research agent's performance against the specific criterion provided. Assign a score from 0.0 to 1.0 
with detailed reasoning based on observable behavior.
</task>

<evaluation_context>
Research agents must balance thoroughness with efficiency, demonstrate strategic thinking, and produce 
high-quality answers. Poor research patterns (excessive searching, lack of reflection, premature stopping) 
lead to incomplete or inefficient research. Your evaluation directly impacts research quality improvement.
</evaluation_context>

<agent_final_answer>
${input.targetToJudge}
</agent_final_answer>

<criterion_to_evaluate>
${input.criterion}
</criterion_to_evaluate>

<evaluation_guidelines>

**Scoring Rubric:**

**0.0-0.3 (POOR)** - Criterion clearly violated or unmet:
- Critical failures in the evaluated aspect
- Clear evidence of ineffective or incorrect behavior
- Multiple violations of best practices
- Outcome significantly misses requirements

**0.4-0.6 (PARTIAL)** - Criterion partially met with significant gaps:
- Some compliance but with notable issues
- Inconsistent application of best practices
- Missing important elements of the criterion
- Outcome has substantial room for improvement

**0.7-0.8 (GOOD)** - Criterion well met with minor issues:
- Strong compliance with criterion requirements
- Best practices generally followed
- Minor inefficiencies or missed optimizations
- Outcome meets expectations with small gaps

**0.9-1.0 (EXCELLENT)** - Criterion exemplarily met:
- Exceptional performance on all criterion aspects
- Optimal behavior and decision-making
- No significant improvements needed
- Outcome exceeds expectations

**Evidence-Based Evaluation:**
- Count actual tool calls (searches, thinkTool usage)
- Track query progression (broad → narrow)
- Identify reflection patterns (when and quality)
- Assess source quality and relevance
- Evaluate stopping decision appropriateness

<evaluation_examples>

Example 1 - Search Efficiency:
Tool History: "tavily-search('climate change'), thinkTool('found overview, need specifics'), 
tavily-search('climate change agriculture 2024'), thinkTool('good sources, can answer'), [stopped]"
Question Complexity: Moderate
SCORE: 0.85
REASONING: Agent used 2 searches for moderate question—efficient. Good broad-to-narrow progression. 
Appropriate stopping after gathering sufficient sources. Minor: could have searched one more specific aspect.

Example 2 - Reflection Quality:
Tool History: "tavily-search('AI trends'), tavily-search('machine learning 2024'), 
tavily-search('AI applications'), tavily-search('AI companies'), thinkTool('found lots of info')"
SCORE: 0.25
REASONING: Agent made 4 searches before using thinkTool once. No reflection after each search as required. 
Final reflection was superficial. Critical violation of reflection discipline.

Example 3 - Answer Completeness:
Sources Found: 6 high-quality articles directly addressing question aspects
Final Answer: Comprehensive response citing all 6 sources, addresses all question parts
SCORE: 0.95
REASONING: Agent gathered 6 relevant sources (exceeds 3+ requirement). Answer thoroughly addresses question 
with proper citations. Excellent completeness and source usage.

Example 4 - Tool Usage Discipline:
Tool History: "tavily-search('topic') + thinkTool('planning next search') [parallel], 
tavily-search('specific aspect')"
SCORE: 0.15
REASONING: Critical violation—agent called thinkTool in parallel with tavily-search, explicitly forbidden. 
This indicates failure to follow tool usage constraints. Major compliance issue.

Example 5 - Stopping Criterion:
Tool History: 5 searches made, last 2 returned very similar information, question answerable after search 3
Final behavior: Continued searching until budget exhausted
SCORE: 0.40
REASONING: Agent failed to stop when appropriate (after 3 searches with sufficient info). Over-researched 
by making 2 redundant searches. Demonstrated poor judgment about when research is complete.

</evaluation_examples>
</evaluation_guidelines>

<output_instructions>
1. **Analyze the tool call history systematically:**
   - Count and categorize tool calls
   - Identify patterns in search queries
   - Track thinkTool usage and quality
   - Note stopping point and reasoning

2. **Extract concrete evidence:**
   - Quote specific tool calls that support your evaluation
   - Reference exact search queries and their progression
   - Point to specific thinkTool reflections (or absence)
   - Identify the stopping point and what information was available

3. **Match evidence to criterion:**
   - Does the behavior align with criterion requirements?
   - Are there violations of specified constraints?
   - How does the outcome compare to expectations?

4. **Assign score based on rubric:**
   - Use the 0.0-1.0 scale with rubric guidelines
   - Be precise (e.g., 0.75, not just "0.7-0.8")
   - Justify the score with specific evidence

5. **Be objective and consistent:**
   - Focus on observable behavior, not assumptions
   - Apply the same standards across all evaluations
   - When uncertain between scores, provide reasoning for your choice

</output_instructions>

<output_format>
ANALYSIS: [3-5 sentences with specific evidence from tool history and final answer. Quote concrete examples.]

EVIDENCE:
- [Specific observation 1 from tool history]
- [Specific observation 2 from tool history]
- [Specific observation 3 about outcome]

SCORE: [0.00-1.00]
</output_format>
        """.trimIndent()
    }
}
