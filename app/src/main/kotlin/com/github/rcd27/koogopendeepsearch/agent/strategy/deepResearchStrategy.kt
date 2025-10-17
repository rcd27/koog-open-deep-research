package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import java.util.*

@Tool
@LLMDescription("Get current date in a human-readable format.")
fun getTodayStr(): String {
    return Date().toLocaleString()
}

fun deepResearchStrategy(
    askUser: suspend (String) -> String
) = strategy<String, String>("scope-strategy") {

    edge(nodeStart forwardTo nodeFinish)
}