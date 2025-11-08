package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleToolsAndSendResults
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

fun getTodayStr(): String {
    return ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

fun deepResearchStrategy(
    askUserTool: (String) -> String
) = strategy<String, String>("deep_research") {
    val clarifyWithUser by subgraphClarifyWithUser(askUserTool)

    val writeResearchBrief: AIAgentNodeBase<String, ResearchQuestion> by nodeWriteResearchBrief()

    val researchSupervisor: AIAgentSubgraph<ResearchQuestion, String> by subgraphResearchSupervisor(3,3)

    val finalReportGeneration by node<String, String>("final_report_generation") { input ->
        input
    }

    nodeStart.then(clarifyWithUser)
        .then(writeResearchBrief)
        .then(researchSupervisor)
        .then(finalReportGeneration)
        .then(nodeFinish)
}
