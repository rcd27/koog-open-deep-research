package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults
import ai.koog.agents.core.dsl.extension.onMultipleAssistantMessages
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import com.github.rcd27.koogopendeepsearch.agent.strategy.subgraphResearcher

// TODO: mcpPrompt not used
fun researcherPrompt(date: String, mcpPrompt: String) = """
You are a research assistant conducting research on the user's input topic.

For context, today's date is $date.

<Task>
Your job is to use tools to gather information about the user's input topic.
You can use any of the tools provided to you to find resources that can help answer the research question. You can call these tools in series or in parallel, your research is conducted in a tool-calling loop.
</Task>

<Available Tools>
You have access to two main tools:
1. **tavily-search**: For conducting web searches to gather information
2. **thinkTool**: For reflection and strategic planning during research
$mcpPrompt

**CRITICAL: Use thinkTool after each search to reflect on results and plan next steps. Do not call thinkTool with the tavily-search or any other tools. It should be to reflect on the results of the search.**
</Available Tools>

<Instructions>
Think like a human researcher with limited time. Follow these steps:

1. **Read the question carefully** - What specific information does the user need?
2. **Start with broader searches** - Use broad, comprehensive queries first
3. **After each search, pause and assess** - Do I have enough to answer? What's still missing?
4. **Execute narrower searches as you gather information** - Fill in the gaps
5. **Stop when you can answer confidently** - Don't keep searching for perfection
</Instructions>

<Hard Limits>
**Tool Call Budgets** (Prevent excessive searching):
- **Simple queries**: Use 2-3 search tool calls maximum
- **Complex queries**: Use up to 5 search tool calls maximum
- **Always stop**: After 5 search tool calls if you cannot find the right sources

**Stop Immediately When**:
- You can answer the user's question comprehensively
- You have 3+ relevant examples/sources for the question
- Your last 2 searches returned similar information
</Hard Limits>

<Show Your Thinking>
After each search tool call, use thinkTool to analyze the results:
- What key information did I find?
- What's missing?
- Do I have enough to answer the question comprehensively?
- Should I search more or provide my answer?
</Show Your Thinking>
""".trimIndent()

fun compressResearchPrompt(date: String) = """
You are a research assistant that has conducted research on a topic by calling several tools and web searches. Your job is now to clean up the findings, but preserve all of the relevant statements and information that the researcher has gathered.
 
For context, today's date is $date.

<Task>
You need to clean up information gathered from tool calls and web searches in the existing messages.
All relevant information should be repeated and rewritten verbatim, but in a cleaner format.
The purpose of this step is just to remove any obviously irrelevant or duplicate information.
For example, if three sources all say "X", you could say "These three sources all stated X".
Only these fully comprehensive cleaned findings are going to be returned to the user, so it's crucial that you don't lose any information from the raw messages.
</Task>

<Tool Call Filtering>
**IMPORTANT**: When processing the research messages, focus only on substantive research content:
- **Include**: All tavily-search results and findings from web searches
- **Exclude**: thinkTool calls and responses - these are internal agent reflections for decision-making and should not be included in the final research report
- **Focus on**: Actual information gathered from external sources, not the agent's internal reasoning process

The thinkTool calls contain strategic reflections and decision-making notes that are internal to the research process but do not contain factual information that should be preserved in the final report.
</Tool Call Filtering>

<Guidelines>
1. Your output findings should be fully comprehensive and include ALL of the information and sources that the researcher has gathered from tool calls and web searches. It is expected that you repeat key information verbatim.
2. This report can be as long as necessary to return ALL of the information that the researcher has gathered.
3. In your report, you should return inline citations for each source that the researcher found.
4. You should include a "Sources" section at the end of the report that lists all of the sources the researcher found with corresponding citations, cited against statements in the report.
5. Make sure to include ALL of the sources that the researcher gathered in the report, and how they were used to answer the question!
6. It's really important not to lose any sources. A later LLM will be used to merge this report with others, so having all of the sources is critical.
</Guidelines>

<Output Format>
The report should be structured like this:
**List of Queries and Tool Calls Made**
**Fully Comprehensive Findings**
**List of All Relevant Sources (with citations in the report)**
</Output Format>

<Citation Rules>
- Assign each unique URL a single citation number in your text
- End with ### Sources that lists each source with corresponding numbers
- IMPORTANT: Number sources sequentially without gaps (1,2,3,4...) in the final list regardless of which sources you choose
- Example format:
  [1] Source Title: URL
  [2] Source Title: URL
</Citation Rules>

Critical Reminder: It is extremely important that any information that is even remotely relevant to the user's research topic is preserved verbatim (e.g. don't rewrite it, don't summarize it, don't paraphrase it).
""".trimIndent()

fun standaloneResearchStrategy(conversationPrompt: Prompt, name: String = "default") =
    strategy<String, String>("standalone_research_strategy_$name") {
        val emulateChatHistory by node<String, String>("emulate_message_history") {
            llm.writeSession {
                prompt = conversationPrompt
            }
            "<bypass/>"
        }
        val researcher: AIAgentNodeBase<String, String> by subgraphResearcher()
        nodeStart then emulateChatHistory then researcher then nodeFinish
    }

fun AIAgentSubgraphBuilderBase<*, *>.subgraphResearcher(): AIAgentSubgraphDelegate<String, String> = subgraph("researcher") {
    val nodeCallLLM by node<String, List<Message.Response>> {
        llm.writeSession {
            appendPrompt {
                system(researcherPrompt(date = getTodayStr(), ""))
            }
            requestLLMMultiple()
        }
    }

    val nodeExecuteTool by nodeExecuteMultipleTools(parallelTools = true)
    val nodeSendToolResult by nodeLLMSendMultipleToolResults()

    val nodeCompressResearch by node<List<Message>, Message.Response>("compress-research") {
        llm.writeSession {
            appendPrompt {
                system(compressResearchPrompt(getTodayStr()))
            }
            requestLLM()
        }
    }

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onMultipleToolCalls { true })
    edge(
        nodeCallLLM forwardTo nodeCompressResearch
            onMultipleAssistantMessages { true }
    )
    edge(
        nodeCompressResearch forwardTo nodeFinish transformed { it.content }
    )

    edge(nodeExecuteTool forwardTo nodeSendToolResult)

    edge(
        nodeSendToolResult forwardTo nodeCompressResearch
            onMultipleAssistantMessages { true }
    )

    edge(nodeSendToolResult forwardTo nodeExecuteTool onMultipleToolCalls { true })
}
