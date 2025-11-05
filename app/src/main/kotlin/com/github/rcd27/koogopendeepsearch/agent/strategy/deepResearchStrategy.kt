package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import java.util.Date

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

    val researchSupervisor: AIAgentNodeBase<ResearchQuestion, String> by nodeResearchSupervisor()

    val finalReportGeneration by node<String, String>("final_report_generation") { input ->
        // At this point, the supervisor has already aggregated researcher outputs (if any)
        // and produced a markdown summary. We simply pass it through or optionally
        // could format/annotate it here in the future.
        input
    }

    nodeStart.then(clarifyWithUser)
        .then(writeResearchBrief)
        .then(researchSupervisor)
        .then(finalReportGeneration)
        .then(nodeFinish)
}
