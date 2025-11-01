package com.github.rcd27.koogopendeepsearch.agent.evaluation

import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import com.github.rcd27.koogopendeepsearch.DeepResearchAgent
import com.github.rcd27.koogopendeepsearch.agent.strategy.ResearchQuestion
import com.github.rcd27.koogopendeepsearch.agent.strategy.nodeWriteResearchBrief
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

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
        researchBriefAgent.evaluateWithRubric(
            input = "",
            criteria = criteria1,
            criterionPromptBuilder = ::briefCriteriaPrompt,
            messageHistory = conversation1
        ) { score: Double ->
            assert(score > 0.8)
        }
    }

    @Test
    fun `evaluate conversation_2`(): Unit = runBlocking {
        val researchBriefAgent = DeepResearchAgent.withStrategy(standaloneResearchBriefStrategy(conversation2))
        researchBriefAgent.evaluateWithRubric(
            input = "",
            criteria = criteria2,
            criterionPromptBuilder = ::briefCriteriaPrompt,
            messageHistory = conversation2
        ) { score: Double ->
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

        fun briefCriteriaPrompt(input: EvaluationInput<ResearchQuestion>) = """
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
${input.targetToJudge}                                                                                               
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
