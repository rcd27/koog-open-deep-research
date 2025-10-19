package com.github.rcd27.koogopendeepsearch.agent.utils

import ai.koog.prompt.message.Message
import ai.koog.prompt.xml.xml

fun List<Message>.foldPromptMessages(): String = xml {
    tag("previous_conversation") {
        this@foldPromptMessages.forEach { message ->
            when (message) {
                is Message.System -> tag("system") { +message.content }
                is Message.Tool.Result -> tag(
                    name = "tool_result",
                    attributes = linkedMapOf(
                        "tool" to message.tool
                    )
                ) { +message.content }

                is Message.User -> tag(name = "user") { +message.content }
                is Message.Assistant -> tag("assistant") { +message.content }
                is Message.Tool.Call -> tag(
                    name = "tool_call",
                    attributes = linkedMapOf(
                        "tool" to message.tool
                    )
                ) { +message.content }
            }
        }
    }
}
