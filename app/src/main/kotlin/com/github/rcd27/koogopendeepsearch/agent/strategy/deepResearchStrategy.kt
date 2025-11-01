package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleToolsAndSendResults
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

    val researcher: AIAgentSubgraph<String, String> by subgraphResearcher()

    val finalReportGeneration by node<String, String>("final_report_generation") { input ->
        input
    }

    nodeStart.then(clarifyWithUser)
        .then(writeResearchBrief)
        .then(researchSupervisor)
        .then(researcher)
        .then(finalReportGeneration)
        .then(nodeFinish)
}
