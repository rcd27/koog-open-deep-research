package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase

fun AIAgentSubgraphBuilderBase<*, *>.nodeAskUser(
    askUser: (String) -> String
): AIAgentNodeDelegate<String, Unit> = node("ask_user") { question ->
    val userAnswer = askUser(question)
    llm.writeSession {
        updatePrompt {
            user(userAnswer)
        }
    }
}