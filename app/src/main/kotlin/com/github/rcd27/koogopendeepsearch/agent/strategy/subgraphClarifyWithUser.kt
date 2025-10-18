package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo

fun AIAgentSubgraphBuilderBase<*, *>.subgraphClarifyWithUser(
    askUserTool: (String) -> String
): AIAgentSubgraphDelegate<String, String> = subgraph("clarify_with_user") {
    val clarify: AIAgentNodeBase<Unit, ClarifyWithUser> by nodeClarify()

    /** Since we don't have any interrupt()-like stuff */
    val askUser: AIAgentNodeBase<String, Unit> by nodeAskUser(askUserTool)

    edge(nodeStart forwardTo clarify transformed {})
    edge(clarify forwardTo askUser onCondition { it.needClarification } transformed { it.question })
    edge(askUser forwardTo clarify transformed {})
    edge(clarify forwardTo nodeFinish onCondition { !it.needClarification } transformed { it.verification })
}