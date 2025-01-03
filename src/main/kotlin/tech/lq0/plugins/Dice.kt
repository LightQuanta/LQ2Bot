package tech.lq0.plugins

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import tech.lq0.interceptor.ChinesePunctuationReplace
import tech.lq0.interceptor.FunctionSwitch
import tech.lq0.utils.replyAndLog

@Component
class Dice {

    @Listener
    @FunctionSwitch("Dice")
    @ChinesePunctuationReplace
    @Filter("\\.?dice( {{count,\\d{1,10}}}d{{max,\\d{1,10}}})?")
    suspend fun OneBotMessageEvent.dice(
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
