package tech.lq0.plugins

import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.models.ChatModel
import com.openai.models.ResponseFormatText
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import kotlinx.serialization.json.Json
import love.forte.simbot.application.Application
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.message.At
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import tech.lq0.config.OpenAIProperties
import tech.lq0.interceptor.FunctionSwitch
import tech.lq0.utils.FunctionCall
import tech.lq0.utils.availableFunctions
import tech.lq0.utils.invokeFunction
import tech.lq0.utils.toText
import kotlin.jvm.optionals.getOrNull

private val prompt = """
用户将输入命令，请根据用户命令和接下来传入的可用函数列表，以JSON格式进行功能调用

示例：用户输入“请展示当前正在开播的主播”
{"name": "showLive"}
""".trimIndent()

private val systemPrompt by lazy {
    arrayOf(
        ChatCompletionMessageParam.ofSystem(
            ChatCompletionSystemMessageParam.builder()
                .content(prompt)
                .build()
        ),
        ChatCompletionMessageParam.ofSystem(
            ChatCompletionSystemMessageParam.builder()
                .content(availableFunctions)
                .build()
        ),
    )
}


private val model = ChatModel.of("deepseek-chat")

@Component
class IntelligentReply @Autowired constructor(val app: Application, val config: OpenAIProperties) {

    private val client = OpenAIOkHttpClient.builder()
        .apiKey(config.apiKey)
        .baseUrl(config.endpoint)
        .build()

    @Listener
    @FunctionSwitch("IntelligentReply")
    suspend fun OneBotNormalGroupMessageEvent.intelligentReply() {
        // 仅响应只@bot且不为回复的消息
        if (messageContent.reference() != null) return
        val atId = messageContent.messages.filterIsInstance<At>()
            .map { it.target.toString() }
            .distinct()
            .takeIf { it.size == 1 }
            ?.single() ?: return

        val bot = runCatching { app.botManagers.firstBot() }.getOrNull() ?: return
        if (atId != bot.id.toString()) return

        // 仅取前100字节省token（）
        val text = messageContent.messages.toList().filter { it !is At }.toText().trim().take(100)

        val prompt = listOf(
            *systemPrompt,
            ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder().content(text).build())
        )

        val completion = client.chat().completions().create(
            ChatCompletionCreateParams.builder()
                .model(model)
                .messages(prompt)
                .maxCompletionTokens(config.maxTokens)
                .responseFormat(ResponseFormatText.builder().type(JsonValue.from("json_object")).build())
                .build()
        )

        val rep = completion.choices()
            .firstOrNull()
            ?.message()
            ?.content()
            ?.getOrNull()
            ?: """{"name":"invalid"}"""

        val call = runCatching { Json.decodeFromString<FunctionCall>(rep) }.getOrElse { FunctionCall("invalid") }
        invokeFunction(call)
    }
}
