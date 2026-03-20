package tech.lq0.plugins

import love.forte.simbot.application.Application
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.message.At
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import tech.lq0.config.OpenAIProperties
import tech.lq0.interceptor.FunctionSwitch
import tech.lq0.utils.*

private val prompt = """
根据用户命令和传入的可用函数列表，返回要调用的功能名称，无法识别则返回invalid

示例：用户输入“展示当前正在开播的主播”
showLive
""".trimIndent()

private val systemPrompt by lazy {
    buildPrompt {
        system(prompt)
        system(availableFunctions)
    }
}

@Component
class IntelligentReply @Autowired constructor(val app: Application, val config: OpenAIProperties) {

    private val client = createOpenAIClient(config)

    @Listener
    @FunctionSwitch("IntelligentReply")
    suspend fun OneBotNormalGroupMessageEvent.intelligentReply() {
        // 仅响应只@bot且不为回复的消息
        if (messageContent.reference() != null) return
        val atId = messageContent.messages.filterIsInstance<At>()
            .map { it.target.toString() }
            .distinct()
            .singleOrNull() ?: return

        val bot = runCatching { app.botManagers.firstBot() }.getOrNull() ?: return
        if (atId != bot.id.toString()) return

        // 仅取前100字节省token（）
        val text = messageContent.messages.toList().filter { it !is At }.toText().trim().take(100)

        val prompt = buildPrompt {
            append(systemPrompt)
            user(text)
        }

        invokeFunction(FunctionCall.parse(client.sendChat(config, prompt)))
    }
}
