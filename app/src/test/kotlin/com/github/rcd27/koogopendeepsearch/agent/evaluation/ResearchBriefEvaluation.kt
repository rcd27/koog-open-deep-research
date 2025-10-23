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
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.structure.StructureFixingParser
import com.github.rcd27.koogopendeepsearch.agent.executor.openAISinglePromptExecutor
import com.github.rcd27.koogopendeepsearch.agent.strategy.ConductResearch
import com.github.rcd27.koogopendeepsearch.agent.strategy.ResearchQuestion
import com.github.rcd27.koogopendeepsearch.agent.strategy.nodeWriteResearchBrief
import com.github.rcd27.koogopendeepsearch.agent.utils.foldPromptMessages
import com.github.rcd27.koogopendeepsearch.testClock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test

/**
 * see: https://academy.langchain.com/courses/take/deep-research-with-langgraph/lessons/67648977-scoping
 */

val conversation_1 = prompt("blabla") {
    user("What's the best way to invest \$50,000 for retirement?")
    assistant("Could you please provide some additional information to tailor the investment advice for your \$50,000 retirement goal? Specifically:\\n Your current age or desired retirement age\\n Your risk tolerance (low, medium, high)\\n Any preferences for investment types (e.g., stocks, bonds, mutual funds, real estate)\\n Whether you are investing through a tax-advantaged account (e.g., IRA, 401(k)) or a regular brokerage account\\n This will help me provide more personalized and relevant suggestions.")
    user("I'm 25 and I want to retire by 45. My risk tolerance is high right now but I think will decrease over time. I have heard that stocks and ETFs are a good choice, but I'm open to anything. And I already have a 401k, but this would just be through a regular brokerage account.")
}

val criteria_1 = listOf(
    "Current age is 25",
    "Desired retirement age is 45",
    "Current risk tolerance is high",
    "Interested in investing in stocks and ETFs",
    "Open to forms of investment beyond stocks and ETFs",
    "Investment account is a regular brokerage account",
)

fun testingStrategy(conversationPrompt: Prompt) = strategy<String, ResearchQuestion>("strategy_with_target_subgraph") {
    val emulateChatHistory by node<String, String>("emulate_message_history") {
        llm.writeSession {
            prompt = conversationPrompt
        }
        "<bypass/>"
    }
    val briefResearch: AIAgentNodeBase<String, ResearchQuestion> by nodeWriteResearchBrief()
    nodeStart then emulateChatHistory then briefResearch then nodeFinish
}

class EvaluationTests {

    // TODO: should be some EVALUATIONS, right? Stats, etc.
    @Test
    fun `research brief evaluation for conversation_1`() = runBlocking { // FIXME: better evaluate in batches
        val targetAgentConfig = AIAgentConfig.withSystemPrompt(
            prompt = "EMPTY",
            maxAgentIterations = 50,
            llm = OpenAIModels.CostOptimized.GPT4oMini // FIXME: overridden by inner nodes
        )
        val evaluationAgent = AIAgent(
            promptExecutor = openAISinglePromptExecutor,
            strategy = testingStrategy(conversation_1),
            agentConfig = targetAgentConfig,
            toolRegistry = ToolRegistry.EMPTY
        )
        val targetResearchQuestion = evaluationAgent.run("")

        val capturedCount = criteria_1.map { criterion ->
            val agentConfig = AIAgentConfig.withSystemPrompt(
                prompt = "EMPTY",
                maxAgentIterations = 50,
                llm = OpenAIModels.CostOptimized.GPT4oMini // FIXME: overridden by inner nodes
            )
            val evaluationAgent = AIAgent(
                promptExecutor = openAISinglePromptExecutor,
                strategy = evaluateSuccessCriteriaStrategy(conversation_1),
                agentConfig = agentConfig,
                toolRegistry = ToolRegistry.EMPTY
            )
            val result = evaluationAgent.run(
                EvaluateSuccessCriteriaInput(
                    criterion, ConductResearch(targetResearchQuestion.researchBrief)
                )
            )
            result
        }
            .fold(0) { acc, i ->
                if (i.isCaptured) acc + 1 else acc
            }

        val score = capturedCount.toDouble() / criteria_1.size
        println("Score: $score")
        assert(score > 0.7)
    }
}

val conversation_2: List<Message> = listOf(
    Message.User(
        "I am looking for an apartment in NYC, can you help me?",
        RequestMetaInfo(testClock.now())
    ),
    Message.Assistant(
        "Could you please specify your apartment preferences? For example:\\n Desired neighborhoods or boroughs\\n Number of bedrooms/bathrooms\\n Budget range (monthly rent)\\n Any amenities or must-have features\\n Preferred move-in date\\n This information will help me provide the most relevant apartment options in NYC.",
        ResponseMetaInfo(testClock.now())
    ),
    Message.User(
        "I'd prefer to live in Chelsea, Flatiron, or West Village. I'm looking for a 2 bed 2 bath, and I am looking for monthly rent below 7k. I'd like this to be a doorman building and have an in unit washer and dryer, but it's okay if there's no washer dryer. It's a plus if the building has a gym. And I'd like to move in in September 2025.",
        RequestMetaInfo(testClock.now())
    )
)

val criteria2 = listOf(
    "Looking for a 2 bed 2 bath apartment in Chelsea, Flatiron, or West Village",
    "Monthly rent below 7k",
    "Should be in a doorman building",
    "Ideally have an in unit washer and dryer but not strict",
    "Ideally have a gym but not strict",
    "Move in date is September 2025"
)

fun briefCriteriaPrompt(criterion: String, researchBrief: ConductResearch) = """
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
$criterion                                                                                                    
</criterion_to_evaluate>                                                                                       
                                                                                                               
<research_brief>                                                                                               
$researchBrief                                                                                               
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

data class EvaluateSuccessCriteriaInput(
    val criterion: String,
    val research: ConductResearch
)

/**
 * LLM judge that evaluates whether research briefs capture specific criteria.
 */
fun evaluateSuccessCriteriaStrategy(messageHistory: Prompt) =
    strategy<EvaluateSuccessCriteriaInput, Criteria>("research_brief_evaluation") {
        /* This is basically copy-paste from LLMAsJudge */
        val judge by node<EvaluateSuccessCriteriaInput, Criteria>("research_brief_evaluation") { input ->
            llm.writeSession {
                val initialPrompt = prompt.copy()
                val initialModel = model

                prompt = prompt("critic") {
                    val combinedMessage = messageHistory.messages.foldPromptMessages()
                    system(briefCriteriaPrompt(input.criterion, input.research))
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

fun <INPUT, OUTPUT> AIAgent<INPUT, OUTPUT>.evaluate(dataset: INPUT): Any {
    TODO("not implemented")
}

fun <INPUT, OUTPUT> AIAgentSubgraph<INPUT, OUTPUT>.evaluate(dataset: INPUT): Any {
    TODO("not implemented")
}

fun <INPUT, OUTPUT> AIAgentNode<INPUT, OUTPUT>.evaluate(dataset: INPUT): Any {
    TODO("not implemented")
}
