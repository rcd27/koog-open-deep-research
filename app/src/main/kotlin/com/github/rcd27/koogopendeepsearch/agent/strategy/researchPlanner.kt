package com.github.rcd27.koogopendeepsearch.agent.strategy

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.markdown.markdown
import kotlinx.serialization.Serializable

@Serializable
data class ResearchPlan(
    @property:LLMDescription("Research topics for standalone research")
    val researchComponents: List<ResearchComponent>
)

@Serializable
data class ResearchComponent(
    @property:LLMDescription("Short, descriptive name of the component")
    val title: String,
    @property:LLMDescription("One-sentence research goal or question")
    val objective: String,
    @property:LLMDescription("2–3 sentences explaining why this subtopic is distinct and important")
    val rationale: String
)

fun AIAgentSubgraphBuilderBase<*, *>.nodeResearchPlanner(): AIAgentNodeDelegate<ResearchQuestion, ResearchPlan> =
    node("node_research_planner") { input ->
        llm.writeSession {
            val initialModel = model.copy()
            model =  OpenAIModels.Chat.GPT4o
            appendPrompt {
            system(
                """
                        **Instruction:**
                        You are an expert research analyst. Your task is to break down the given **Research Brief** into **3–5 distinct research components**, each of which could be investigated independently by separate agents or teams.

                        Each component should:

                        1. Represent a **self-contained research goal or question**.
                        2. Be **logically derived** from the main brief but **not overlap** with the others.
                        3. Include a **short rationale** explaining why it is relevant and distinct.
                        4. Be phrased as a clear and actionable **research objective or question**.

                        **Output format (JSON preferred):**

                        ```json
                        {
                          "researchСomponents": [
                            {
                              "title": "Short, descriptive name of the component",
                              "objective": "One-sentence research goal or question",
                              "rationale": "2–3 sentences explaining why this subtopic is distinct and important"
                            }
                          ]
                        }
                        ```

                        **Input:**
                        A single Research Brief (short or long text).

                        **Output:**
                        A structured list of 3–5 independent research components.

                        **Example Input:**
                        "Investigate the effects of AI on the job market, including automation, new skill demands, and ethical implications."

                        **Example Output:**

                        ```json
                        {
                          "research_components": [
                            {
                              "title": "Automation and Workforce Displacement",
                              "objective": "Analyze how AI-driven automation affects employment rates in different industries.",
                              "rationale": "This focuses on the direct labor market impact of AI automation and identifies the most vulnerable sectors."
                            },
                            {
                              "title": "Evolving Skill Requirements",
                              "objective": "Study how AI adoption changes the demand for specific technical and soft skills.",
                              "rationale": "This explores how workforce education and training must adapt to new skill demands created by AI."
                            },
                            {
                              "title": "Ethical and Societal Implications",
                              "objective": "Examine ethical concerns and societal consequences of widespread AI deployment in the workplace.",
                              "rationale": "This addresses non-economic dimensions, ensuring the study covers moral and social aspects of AI adoption."
                            }
                          ]
                        }
                        ```
                """.trimIndent()
            )
            user(
                markdown {
                    h2("Research Brief")
                    br()
                    text(input.researchBrief)
                }
            )
        }
        val response = requestLLMStructured<ResearchPlan>().getOrThrow().structure
        model = initialModel
        response
    }
}
