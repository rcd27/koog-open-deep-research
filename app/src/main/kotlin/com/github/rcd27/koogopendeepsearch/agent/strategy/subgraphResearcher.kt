package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.prompt.message.Message

fun researcherPrompt(date: String, mcpPrompt: String) = """
You are a research assistant conducting research on the user's input topic.

For context, today's date is ${date}.

<Task>
Your job is to use tools to gather information about the user's input topic.
You can use any of the tools provided to you to find resources that can help answer the research question. You can call these tools in series or in parallel, your research is conducted in a tool-calling loop.
</Task>

<Available Tools>
You have access to two main tools:
1. **tavily_search**: For conducting web searches to gather information
2. **think_tool**: For reflection and strategic planning during research
$mcpPrompt

**CRITICAL: Use think_tool after each search to reflect on results and plan next steps. Do not call think_tool with the tavily_search or any other tools. It should be to reflect on the results of the search.**
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
After each search tool call, use think_tool to analyze the results:
- What key information did I find?
- What's missing?
- Do I have enough to answer the question comprehensively?
- Should I search more or provide my answer?
</Show Your Thinking>
""".trimIndent()

fun AIAgentSubgraphBuilderBase<*, *>.subgraphResearcher(
    maxStructuredOutputRetries: Int = 3
): AIAgentSubgraphDelegate<String, String> = subgraph("researcher") {
    require(maxStructuredOutputRetries > 0) { "Output retries must be greater than 0" }
    val researchStepKey = createStorageKey<Int>("reasoning_step")
    val nodeSetup by node<String, String> {
        storage.set(researchStepKey, 0)
        it
    }
    val nodeCallLLM by node<Unit, Message.Response> {
        llm.writeSession {
            updatePrompt {
                system(researcherPrompt(date = getTodayStr(), ""))
            }
            requestLLM()
        }
    }
    val nodeExecuteTool by nodeExecuteTool()

    val reasoningPrompt = "Please give your thoughts about the task and plan the next steps."
    val nodeCallLLMReasonInput by node<String, Unit> { stageInput ->
        llm.writeSession {
            updatePrompt {
                user(stageInput)
                user(reasoningPrompt)
            }

            requestLLMWithoutTools()
        }
    }
    val nodeCallLLMReason by node<ReceivedToolResult, Unit> { result ->
        val reasoningStep = storage.getValue(researchStepKey)
        llm.writeSession {
            updatePrompt {
                tool {
                    result(result)
                }
            }

            if (reasoningStep % maxStructuredOutputRetries == 0) {
                updatePrompt {
                    user(reasoningPrompt)
                }
                requestLLMWithoutTools()
            }
        }
        storage.set(researchStepKey, reasoningStep + 1)
    }

    edge(nodeStart forwardTo nodeSetup)
    edge(nodeSetup forwardTo nodeCallLLMReasonInput)
    edge(nodeCallLLMReasonInput forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeCallLLMReason)
    edge(nodeCallLLMReason forwardTo nodeCallLLM)
}
