package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.dsl.builder.forwardTo
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
    askUser: suspend (String) -> String
) = strategy<String, String>("deep_research") {

    /** #1: Top-level strategy definition */
    val clarifyWithUser by clarifyWithUser("clarify_with_user")

    // TODO: move to clarifyWithUserSubgraph
    /** Since we don't have any interrupt()-like stuff */
    val askUser by node<String, Unit> { question ->
        val userAnswer = askUser(question)
        llm.writeSession {
            updatePrompt {
                user(userAnswer)
            }
        }
    }

    val writeResearchBrief by node<String, String>("write_research_brief") { input ->
        input
    }

    val researchSupervisor by node<String, String>("research_supervisor") { input ->
        input
    }

    val finalReportGeneration by node<String, String>("final_report_generation") { input ->
        input
    }

    edge(nodeStart forwardTo clarifyWithUser transformed {})

    edge(clarifyWithUser forwardTo askUser onCondition { it.needClarification } transformed { it.question })
    edge(askUser forwardTo clarifyWithUser transformed {})
    edge(clarifyWithUser forwardTo writeResearchBrief onCondition { !it.needClarification } transformed { it.verification })

    writeResearchBrief then researchSupervisor then finalReportGeneration then nodeFinish
}