package com.github.rcd27.koogopendeepsearch.agent.executor

import com.github.rcd27.koogopendeepsearch.Config
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.*


val httpClientWithProxy = createHttpClientWithOptionalProxy(Config.PROXY_URL)

val openAISinglePromptExecutor = SingleLLMPromptExecutor(
    OpenAILLMClient(
        apiKey = Config.OPENAI_API_KEY,
        baseClient = httpClientWithProxy
    )
)

fun createHttpClientWithOptionalProxy(proxyUrl: String?): HttpClient {
    return if (proxyUrl != null) {
        HttpClient(CIO) {
            engine {
                proxy = ProxyBuilder.http(Url(proxyUrl))
            }
            install(HttpTimeout)
        }
    } else {
        HttpClient(CIO) {
            install(HttpTimeout)
        }
    }
}