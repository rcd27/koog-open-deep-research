package com.github.rcd27.koogopendeepsearch.agent.evaluation

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.structure.StructureFixingParser
import com.github.rcd27.koogopendeepsearch.DeepResearchAgent
import com.github.rcd27.koogopendeepsearch.agent.executor.openAISinglePromptExecutor
import com.github.rcd27.koogopendeepsearch.agent.strategy.ResearchQuestion
import com.github.rcd27.koogopendeepsearch.agent.strategy.nodeWriteResearchBrief
import com.github.rcd27.koogopendeepsearch.agent.utils.foldPromptMessages
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test

/**
 * see: https://academy.langchain.com/courses/take/deep-research-with-langgraph/lessons/67648977-scoping
 */

fun standaloneResearchBriefStrategy(conversationPrompt: Prompt) =
    strategy<String, ResearchQuestion>("strategy_with_target_subgraph") {
        val emulateChatHistory by node<String, String>("emulate_message_history") {
            llm.writeSession {
                prompt = conversationPrompt
            }
            "<bypass/>"
        }
        val researchBrief: AIAgentNodeBase<String, ResearchQuestion> by nodeWriteResearchBrief()
        nodeStart then emulateChatHistory then researchBrief then nodeFinish
    }

class ResearchBriefEvaluation {

    @Test
    fun `evaluate conversation_1`(): Unit = runBlocking {
        val researchBriefAgent = DeepResearchAgent.withStrategy(standaloneResearchBriefStrategy(conversation1))
        researchBriefAgent.evaluate("", criteria1, conversation1) { score: Double ->
            assert(score > 0.8)
        }
    }

    @Test
    fun `evaluate conversation_2`(): Unit = runBlocking {
        val researchBriefAgent = DeepResearchAgent.withStrategy(standaloneResearchBriefStrategy(conversation2))
        researchBriefAgent.evaluate("", criteria2, conversation2) { score: Double ->
            assert(score > 0.8)
        }
    }

    private companion object {
        //region dataset
        val conversation1 = prompt("conversation_1") {
            user("What's the best way to invest $50,000 for retirement?")
            assistant("Could you please provide some additional information to tailor the investment advice for your $50,000 retirement goal? Specifically:\\n Your current age or desired retirement age\\n Your risk tolerance (low, medium, high)\\n Any preferences for investment types (e.g., stocks, bonds, mutual funds, real estate)\\n Whether you are investing through a tax-advantaged account (e.g., IRA, 401(k)) or a regular brokerage account\\n This will help me provide more personalized and relevant suggestions.")
            user("I'm 25 and I want to retire by 45. My risk tolerance is high right now but I think will decrease over time. I have heard that stocks and ETFs are a good choice, but I'm open to anything. And I already have a 401k, but this would just be through a regular brokerage account.")
        }

        val criteria1 = listOf(
            "Current age is 25",
            "Desired retirement age is 45",
            "Current risk tolerance is high",
            "Interested in investing in stocks and ETFs",
            "Open to forms of investment beyond stocks and ETFs",
            "Investment account is a regular brokerage account",
        )

        val conversation2 = prompt("conversation_2") {
            user("I'm looking for an apartment in NYC, can you help me?")
            assistant(
                "Could you please specify your apartment preferences? For example:\\n Desired neighborhoods or boroughs\\n Number of bedrooms/bathrooms\\n Budget range (monthly rent)\\n Any amenities or must-have features\\n Preferred move-in date\\n This information will help me provide the most relevant apartment options in NYC."
            )
            user(
                "I'd prefer to live in Chelsea, Flatiron, or West Village. I'm looking for a 2 bed 2 bath, and I am looking for monthly rent below 7k. I'd like this to be a doorman building and have an in unit washer and dryer, but it's okay if there's no washer dryer. It's a plus if the building has a gym. And I'd like to move in in September 2025."
            )
        }

        val criteria2 = listOf(
            "Looking for a 2 bed 2 bath apartment in Chelsea, Flatiron, or West Village",
            "Monthly rent below 7k",
            "Should be in a doorman building",
            "Ideally have an in unit washer and dryer but not strict",
            "Ideally have a gym but not strict",
            "Move in date is September 2025"
        )
        //endregion

        //region Criteria
        data class EvaluateSuccessCriteriaInput<T>(
            val criterion: String,
            val research: T
        )

        @LLMDescription(
            "    Individual success criteria evaluation result.\n" +
                "    \n" +
                "    This model represents a single evaluation criteria that should be present\n" +
                "    in the research brief, along with a detailed assessment of whether it was\n" +
                "    successfully captured and the reasoning behind that assessment."
        )
        @Serializable
        data class Criteria(
            @property:LLMDescription("The specific success criteria being evaluated (e.g., 'Current age is 25', 'Monthly rent below 7k')")
            val criteriaText: String,
            @property:LLMDescription("Detailed explanation of why this criteria is or isn't captured in the research brief, including specific evidence from the brief")
            val reasoning: String,
            @property:LLMDescription("Whether this specific criteria is adequately captured in the research brief (True) or missing/inadequately addressed (False)")
            val isCaptured: Boolean
        )

        /**
         * LLM judge that evaluates whether research briefs capture specific criteria.
         */
        fun <T> evaluateSuccessCriteriaStrategy(messageHistory: Prompt) =
            strategy<EvaluateSuccessCriteriaInput<T>, Criteria>("research_brief_evaluation") {
                /* This is basically copy-paste from LLMAsJudge but with Criteria output */
                val judge by node<EvaluateSuccessCriteriaInput<T>, Criteria>("judge") { input: EvaluateSuccessCriteriaInput<T> ->
                    llm.writeSession {
                        val initialPrompt = prompt.copy()
                        val initialModel = model.copy()

                        prompt = prompt("critic") {
                            val combinedMessage = messageHistory.messages.foldPromptMessages()
                            system(briefCriteriaPrompt(input))
                            user(combinedMessage)
                        }

                        val result = requestLLMStructured<Criteria>(
                            examples = emptyList(),
                            fixingParser = StructureFixingParser(
                                fixingModel = OpenAIModels.CostOptimized.GPT4oMini,
                                retries = 3,
                            )
                        ).getOrThrow().structure

                        prompt = initialPrompt
                        model = initialModel
                        result
                    }
                }

                nodeStart then judge then nodeFinish
            }

        fun <INPUT, OUTPUT> AIAgent<INPUT, OUTPUT>.evaluate(
            targetInput: INPUT,
            criteria: List<String>,
            conversation: Prompt,
            scoring: (Double) -> Unit
        ) = runBlocking {
            val targetResearchQuestion = this@evaluate.run(targetInput)
            val capturedCount = criteria.map { criterion ->
                val evaluationAgentConfig = AIAgentConfig.withSystemPrompt(
                    prompt = "EMPTY",
                    maxAgentIterations = 50,
                    llm = OpenAIModels.Chat.GPT4o // <<< GPT4o for judging llm-rubric
                )
                val evaluationAgent = AIAgent(
                    promptExecutor = openAISinglePromptExecutor,
                    strategy = evaluateSuccessCriteriaStrategy<OUTPUT>(conversation),
                    agentConfig = evaluationAgentConfig,
                    toolRegistry = ToolRegistry.EMPTY
                )
                val result: Criteria = evaluationAgent.run(
                    EvaluateSuccessCriteriaInput(
                        criterion,
                        targetResearchQuestion
                    )
                )
                result
            }
                .fold(0) { acc, i ->
                    if (i.isCaptured) acc + 1 else acc
                }

            val score: Double = capturedCount.toDouble() / criteria.size
            scoring.invoke(score)
        }

        // note: llm-rubric assert value
        fun <T> briefCriteriaPrompt(input: EvaluateSuccessCriteriaInput<T>) = """
<role>                                                                                                         
You are an expert research brief evaluator specializing in assessing whether generated research briefs         
accurately capture user-specified criteria without loss of important details.                                  
</role>                                                                                                        
                                                                                                               
<task>                                                                                                         
Determine if the research brief adequately captures the specific success criterion provided. Return a binary   
assessment with detailed reasoning.                                                                            
</task>                                                                                                        
                                                                                                               
<evaluation_context>                                                                                           
Research briefs are critical for guiding downstream research agents. Missing or inadequately captured          
criteria can lead to incomplete research that fails to address user needs. Accurate evaluation ensures         
research quality and user satisfaction.                                                                        
</evaluation_context>                                                                                          
                                                                                                               
<criterion_to_evaluate>                                                                                        
${input.criterion}                                                                                                    
</criterion_to_evaluate>                                                                                       
                                                                                                               
<research_brief>                                                                                               
${input.research}                                                                                               
</research_brief>                                                                                              
                                                                                                               
<evaluation_guidelines>                                                                                        
CAPTURED (criterion is adequately represented) if:                                                             
- The research brief explicitly mentions or directly addresses the criterion                                   
- The brief contains equivalent language or concepts that clearly cover the criterion                          
- The criterion's intent is preserved even if worded differently                                               
- All key aspects of the criterion are represented in the brief                                                
                                                                                                               
NOT CAPTURED (criterion is missing or inadequately addressed) if:                                              
- The criterion is completely absent from the research brief                                                   
- The brief only partially addresses the criterion, missing important aspects                                  
- The criterion is implied but not clearly stated or actionable for researchers                                
- The brief contradicts or conflicts with the criterion                                                        
                                                                                                               
<evaluation_examples>                                                                                          
Example 1 - CAPTURED:                                                                                          
Criterion: "Current age is 25"                                                                                 
Brief: "...investment advice for a 25-year-old investor..."                                                    
Judgment: CAPTURED - age is explicitly mentioned                                                               
                                                                                                               
Example 2 - NOT CAPTURED:                                                                                      
Criterion: "Monthly rent below 7k"                                                                             
Brief: "...find apartments in Manhattan with good amenities..."                                                
Judgment: NOT CAPTURED - budget constraint is completely missing                                               
                                                                                                               
Example 3 - CAPTURED:                                                                                          
Criterion: "High risk tolerance"                                                                               
Brief: "...willing to accept significant market volatility for higher returns..."                              
Judgment: CAPTURED - equivalent concept expressed differently                                                  
                                                                                                               
Example 4 - NOT CAPTURED:                                                                                      
Criterion: "Doorman building required"                                                                         
Brief: "...find apartments with modern amenities..."                                                           
Judgment: NOT CAPTURED - specific doorman requirement not mentioned                                            
</evaluation_examples>                                                                                         
</evaluation_guidelines>                                                                                       
                                                                                                               
<output_instructions>                                                                                          
1. Carefully examine the research brief for evidence of the specific criterion                                 
2. Look for both explicit mentions and equivalent concepts                                                     
3. Provide specific quotes or references from the brief as evidence                                            
4. Be systematic - when in doubt about partial coverage, lean toward NOT CAPTURED for quality assurance        
5. Focus on whether a researcher could act on this criterion based on the brief alone                          
</output_instructions>
        """.trimIndent()
    }
}

fun <INPUT, OUTPUT> AIAgentSubgraph<INPUT, OUTPUT>.evaluate(dataset: INPUT): Any {
    TODO("not implemented")
}

fun <INPUT, OUTPUT> AIAgentNode<INPUT, OUTPUT>.evaluate(dataset: INPUT): Any {
    TODO("not implemented")
}
