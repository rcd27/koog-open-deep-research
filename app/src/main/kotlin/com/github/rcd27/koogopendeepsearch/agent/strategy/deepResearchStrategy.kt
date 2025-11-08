package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.dsl.builder.strategy
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun getTodayStr(): String {
    return ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

fun deepResearchStrategy(
    askUserTool: (String) -> String
) = strategy<String, String>("deep_research") {
    val clarifyWithUser: AIAgentSubgraph<String, String> by subgraphClarifyWithUser(askUserTool)

    val writeResearchBrief: AIAgentNodeBase<String, ResearchQuestion> by nodeWriteResearchBrief()

    val researchPlanner: AIAgentNodeBase<ResearchQuestion, ResearchPlan> by nodeResearchPlanner()

    // TODO: implement
//    val researchSupervisor: AIAgentNodeBase<ResearchQuestion, String> by nodeResearchSupervisor()

    val researcher: AIAgentSubgraph<String, String> by subgraphResearcher()

    val parallelResearch by parallel(researcher, researcher, researcher, researcher) {
        fold("", { acc, result -> acc + result })
    }

    // TODO: implement
    val finalReportGeneration by node<String, String>("final_report_generation") { input ->
        input
    }

    nodeStart.then(clarifyWithUser)
        .then(writeResearchBrief)
        .then(researchPlanner)
//        .then(researchSupervisor)
//        .then(researcher)
//        .then(finalReportGeneration)
//        .then(nodeFinish)
}
