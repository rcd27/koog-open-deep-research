package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.markdown
import com.github.rcd27.koogopendeepsearch.DeepResearchAgent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    val executeNode by node<ResearchPlan, String>("supervisor_execute") { researchPlan ->
        val sem = Semaphore(researchPlan.researchComponents.size)
        val results: List<String> = coroutineScope {
            researchPlan.researchComponents.map { component ->
                async {
                    sem.withPermit {
                        val researcherAgent = DeepResearchAgent.withStrategy(
                            standaloneResearchStrategy(
                                prompt("researcher") {
                                    system {
                                        markdown {
                                            h2(component.title)
                                            br()
                                            text(component.objective)
                                            br()
                                            text(component.rationale)
                                        }
                                    }
                                },
                                name = component.title
                            )
                        )
                        researcherAgent.run("")
                    }
                }
            }.awaitAll()
        }

        val findingsSummary = results.joinToString(separator = "\n\n") { it }
        findingsSummary
    }

    val finalReportGeneration by node<String, String>("final_report_generation") { input ->
        llm.writeSession {
            appendPrompt {
                system("""Your goal is to compress research provided by user, provide nicely printed markdown 
                    |document with key studies and links to sources""".trimMargin())
                user(input)
            }
            requestLLM().content
        }
    }

    nodeStart.then(clarifyWithUser)
        .then(writeResearchBrief)
        .then(researchPlanner)
        .then(executeNode)
        .then(finalReportGeneration)
        .then(nodeFinish)
}
