package com.github.rcd27.koogopendeepsearch

import io.github.cdimascio.dotenv.dotenv

object Config {
    private val dotenv = dotenv {
        directory = "./app"
    }

    val PROXY_URL: String? = dotenv["PROXY_URL"]
    val TAVILY_API_KEY = dotenv["TAVILY_API_KEY"] ?: error("No TAVILY_API_KEY in .env file")
    val OPENAI_API_KEY = dotenv["OPENAI_API_KEY"] ?: error("No OPENAI_API_KEY in .env file")
    val LANGFUSE_HOST = dotenv["LANGFUSE_HOST"] ?: error("No LANGFUSE_HOST in .env file")
    val LANGFUSE_PUBLIC_KEY = dotenv["LANGFUSE_PUBLIC_KEY"] ?: error("No LANGFUSE_PUBLIC_KEY in .env file")
    val LANGFUSE_SECRET_KEY = dotenv["LANGFUSE_SECRET_KEY"] ?: error("No LANGFUSE_SECRET_KEY in .env file")
}
