package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.structure.StructureFixingParser
import com.github.rcd27.koogopendeepsearch.agent.utils.foldPromptMessages
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

fun clarifyWithUserInstructions(messages: String, date: String): String = """
These are the messages that have been exchanged so far from the user asking for the report:
<messages>
$messages
</messages>

Today's date is $date.

Assess whether you need to ask a clarifying question, or if the user has already provided enough information for you to start research.
IMPORTANT: If you can see in the messages history that you have already asked a clarifying question, you almost always do not need to ask another one. Only ask another question if ABSOLUTELY NECESSARY.

If there are acronyms, abbreviations, or unknown terms, ask the user to clarify.
If you need to ask a question, follow these guidelines:
- Be concise while gathering all necessary information
- Make sure to gather all the information needed to carry out the research task in a concise, well-structured manner.
- Use bullet points or numbered lists if appropriate for clarity. Make sure that this uses markdown formatting and will be rendered correctly if the string output is passed to a markdown renderer.
- Don't ask for unnecessary information, or information that the user has already provided. If you can see that the user has already provided the information, do not ask for it again.

Respond in valid JSON format with these exact keys:
"needClarification": boolean,
"question": "<question to ask the user to clarify the report scope>",
"verification": "<verification message that we will start research>"

If you need to ask a clarifying question, return:
"needClarification": true,
"question": "<your clarifying question>",
"verification": ""

If you do not need to ask a clarifying question, return:
"needClarification": false,
"question": "",
"verification": "<acknowledgement message that you will now start research based on the provided information>"

For the verification message when no clarification is needed:
- Acknowledge that you have sufficient information to proceed
- Briefly summarize the key aspects of what you understand from their request
- Confirm that you will now begin the research process
- Keep the message concise and professional
""".trimIndent()

@Serializable
@LLMDescription("Schema for user clarification decision and questions.")
data class ClarifyWithUser(
    @property:LLMDescription("Whether the user needs to be asked a clarifying question.")
    val needClarification: Boolean, // FIXME: split into sealed class without flags
    @property:LLMDescription("A question to ask the user to clarify the report scope.")
    val question: String,
    @property:LLMDescription("Verify message that we will start research after the user has provided the necessary information.")
    val verification: String
)

/**
 * Analyze user messages and ask clarifying questions if the research scope is unclear.
 *
 * This function determines whether the user's request needs clarification before proceeding
 * with research. If clarification is disabled or not needed, it proceeds directly to research.
 */
@OptIn(ExperimentalTime::class)
fun AIAgentSubgraphBuilderBase<*, *>.nodeClarify(): AIAgentNodeDelegate<Unit, ClarifyWithUser> =
    node("clarify") { nodeInput ->
        llm.writeSession {
            val initialPrompt = prompt

            /** Langchain implementation treats nodes as standalone agents with their own system_prompt */
            prompt = prompt("clarify_with_user_instructions_prompt") {
                system(
                    clarifyWithUserInstructions(
                        messages = initialPrompt.messages.foldPromptMessages(),
                        date = getTodayStr()
                    )
                )
            }

            val llmStructuredResult: ClarifyWithUser = requestLLMStructured(
                examples = listOf(
                    ClarifyWithUser(
                        needClarification = true,
                        question = "What is the model of a car you want to buy?",
                        verification = ""
                    ),
                    ClarifyWithUser(
                        needClarification = false,
                        question = "",
                        verification = "The chosen car is Honda Civic FD8, 1.8L"
                    ),
                ),
                fixingParser = StructureFixingParser(
                    fixingModel = OpenAIModels.CostOptimized.GPT4oMini,
                    retries = 3,
                )
            ).getOrThrow().structure

            prompt = initialPrompt

            return@writeSession llmStructuredResult
        }
    }
