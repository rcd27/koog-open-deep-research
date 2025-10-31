package com.github.rcd27.koogopendeepsearch.agent.evaluation

import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import com.github.rcd27.koogopendeepsearch.DeepResearchAgent
import com.github.rcd27.koogopendeepsearch.agent.strategy.subgraphResearcher
import com.github.rcd27.koogopendeepsearch.agent.utils.foldPromptMessages
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.io.File

val messagesShouldContinue = prompt("agent-should-continue") {
    user("What are the top coffee shops in San Francisco based on coffee quality?")
    assistant("I'll search for information about top coffee shops in San Francisco focusing on coffee quality.")
    tool {
        call(
            id = "call_continue_search_001",
            content = """
                {"query": "coffee shops San Francisco popular locations"}
            """.trimIndent(),
            tool = "tavily_search"
        )
        result(
            id = "call_continue_search_001",
            tool = "tavily_search",
            content = """
                {"result": "Search results show popular San Francisco cafes: Blue Bottle Coffee has great wifi and 
                ambiance, Philz Coffee is known for custom blends and friendly service, Café Reveille has multiple 
                locations with good pastries and laptop-friendly spaces. Ritual Coffee offers cozy seating and is popular with students."},
            """.trimIndent()
        )
    }
    assistant("Let me use the think_tool to analyze these search results and determine next steps.")
    tool {
        call(
            id = "call_continue_search_002",
            content = """
                {"args": {"reflection": "Analysis of search results: The information I found focuses on general cafe 
                features like wifi, ambiance, custom blends, and seating - but lacks specific coffee quality metrics.
                 The user specifically asked for evaluation based on coffee quality, which would require expert reviews, professional ratings, specialty certifications, or quality-focused customer feedback. I need to search specifically for coffee quality assessments, Coffee Review scores, specialty coffee credentials, or expert evaluations of SF coffee shops. The current information is insufficient to answer the user's question about coffee quality."}}
            """.trimIndent(),
            tool = "think_tool"
        )
        result(
            id = "call_continue_search_002",
            content = """
                {"result": "Reflection recorded: Analysis of search results: The information I found focuses on general cafe features like wifi, ambiance, custom blends, and seating - but lacks specific coffee quality metrics. The user specifically asked for evaluation based on coffee quality, which would require expert reviews, professional ratings, specialty certifications, or quality-focused customer feedback. I need to search specifically for coffee quality assessments, Coffee Review scores, specialty coffee credentials, or expert evaluations of SF coffee shops. The current information is insufficient to answer the user's question about coffee quality."}
            """.trimIndent(),
            tool = "think_tool"
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
            tool = "tavily_search"
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
            tool = "tavily_search"
        )
    }
    assistant("Let me reflect on the search results using the think_tool to assess whether I have sufficient information.")
    tool {
        call(
            id = "call_continue_search_002",
            content = """
                {"args": {"reflection": "Analysis of search results: The information I found focuses on general cafe 
                features like wifi, ambiance, custom blends, and seating - but lacks specific coffee quality metrics."}}
            """,
            tool = "think_tool"
        )
        result(
            id = "call_continue_search_002",
            content = """
                {"result": "Reflection recorded: Analysis of search results: The information I found focuses on general cafe features like wifi, ambiance, custom blends, and seating - but lacks specific coffee quality metrics."}
            """.trimIndent(),
            tool = "think_tool"
        )
    }
}

// TODO: replace with llmJudge
@Serializable
data class ShouldContinue(
    @property:LLMDescription("Should continue research?")
    val shouldContinue: Boolean
)

fun standaloneResearchStrategy(conversationPrompt: Prompt) =
    strategy<String, ShouldContinue>("standalone_research_strategy") {
        val emulateChatHistory by node<String, String>("emulate_message_history") {
            llm.writeSession {
                prompt = conversationPrompt
            }
            "<bypass/>"
        }
        val researcher: AIAgentNodeBase<String, String> by subgraphResearcher(3)
        val shouldContinue by nodeLLMRequestStructured<ShouldContinue>()
        nodeStart then emulateChatHistory then researcher
        edge(
            researcher forwardTo shouldContinue
                transformed { input ->
                llm.writeSession {
                    updatePrompt {
                        user("Make a decision, if research is finished according to the information I've gathered, or continue researching.")
                    }
                }
                input
            }
        )
        edge(
            shouldContinue forwardTo nodeFinish transformed { it.getOrNull()!!.structure }
        )
    }

class ResearcherEvaluation {

    @Test
    fun `print prompts to file`() {
        File("./messagesShouldContinue.txt").run {
            val json = Json { prettyPrint = true }
            val output = messagesShouldContinue.messages.foldPromptMessages()
            writeText(output)
        }
    }

    @Test
    fun `Should continue research`(): Unit = runBlocking {
        val researcherAgent = DeepResearchAgent.withStrategy(standaloneResearchStrategy(messagesShouldContinue))
        val targetResearch = researcherAgent.run("")
        assert(targetResearch.shouldContinue)
    }

    @Test
    fun `Should stop research`(): Unit = runBlocking {
        val researcherAgent = DeepResearchAgent.withStrategy(standaloneResearchStrategy(messagesShouldStop))
        val targetResearch = researcherAgent.run("")
        assert(!targetResearch.shouldContinue)
    }
}
