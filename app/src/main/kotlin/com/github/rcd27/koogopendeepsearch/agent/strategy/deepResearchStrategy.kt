package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import java.util.*

@Tool
@LLMDescription("Get current date in a human-readable format.")
fun getTodayStr(): String {
    return Date().toLocaleString() // FIXME: use relevant method for fetching local machine date-time
}

fun deepResearchStrategy(
    askUserTool: (String) -> String
) = strategy<String, String>("deep_research") {
    val clarifyWithUser by subgraphClarifyWithUser(askUserTool)

    val writeResearchBrief by nodeWriteResearchBrief()

    val researchSupervisor by node<ResearchQuestion, String>("research_supervisor") { input ->
        input.researchBrief
    }

    val finalReportGeneration by node<String, String>("final_report_generation") { input ->
        input
    }

    nodeStart.then(clarifyWithUser)
        .then(writeResearchBrief)
        .then(researchSupervisor)
        .then(finalReportGeneration)
        .then(nodeFinish)
}
