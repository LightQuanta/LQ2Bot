package tech.lq0.plugins

import kotlinx.serialization.Serializable
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import tech.lq0.config.OpenAIProperties
import tech.lq0.interceptor.ChinesePunctuationReplace
import tech.lq0.interceptor.FunctionSwitch
import tech.lq0.utils.*

@Component
class Dice @Autowired constructor(val config: OpenAIProperties) {

    val client = createOpenAIClient(config)

    @Serializable
    data class DiceParameter(
        val count: Int = 1,
        val maxPoint: Int = 6,
    )

    val prompt = buildPrompt {
        system("将骰子数量和骰子面数解析为JSON")
        system(generateSchema<DiceParameter>())
    }

    @AiFunction("骰子")
    @FunctionSwitch("Dice")
    suspend fun OneBotNormalGroupMessageEvent.dice() {
        val resp = client.getJsonResponse<DiceParameter>(config, buildPrompt {
            append(prompt)
            user(messageContent.messages.toTextWithoutAt())
        }) ?: DiceParameter()

        diceWithParameter(resp)
    }

    suspend fun OneBotNormalGroupMessageEvent.diceWithParameter(parameter: DiceParameter) {
        replyAndLog(generateDiceResult(parameter.maxPoint, parameter.count))
    }

    @Listener
    @FunctionSwitch("Dice")
    @ChinesePunctuationReplace
    @Filter("\\.?dice( {{count,\\d{1,10}}}d{{max,\\d{1,10}}})?")
    suspend fun OneBotMessageEvent.diceCommand(
        @FilterValue("max") max: Int = 6,
        @FilterValue("count") count: Int = 1,
    ) = replyAndLog(generateDiceResult(max, count))

    fun generateDiceResult(max: Int, count: Int): String {
        val maximumPoint = max.coerceAtLeast(1)
        val diceCount = count.coerceIn(1..10)

        val result = Array(diceCount) { (1..maximumPoint).random() }.joinToString()
        return "你投出了$result！"
    }

}
