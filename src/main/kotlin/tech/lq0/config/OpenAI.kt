package tech.lq0.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "openai")
data class OpenAIProperties(
    var apiKey: String = "",
    var endpoint: String = "https://api.deepseek.com",
    var maxTokens: Long = 500,
    var timeout: Long = 60000,
)