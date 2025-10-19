package com.github.rcd27.koogopendeepsearch.agent.executor

import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import com.github.rcd27.koogopendeepsearch.Config
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.Url

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
