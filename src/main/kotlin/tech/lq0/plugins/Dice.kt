package tech.lq0.plugins

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotFriendMessageEvent
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component

@Component
class Dice {
    // TODO 群聊实现，启用/禁用处理等

    @Listener
    @Filter("dice( {{max,\\d{1,10}}}d{{count,\\d{1,10}}})?")
    suspend fun OneBotFriendMessageEvent.dice(
        @FilterValue("max") max: Int = 6,
        @FilterValue("count") count: Int = 1,
    ) {
        val maximumPoint = max.coerceAtLeast(1)
        val diceCount = count.coerceIn(1..10)

        val result = Array(diceCount) { (1..maximumPoint).random()}.joinToString()
        reply("你投出了$result！")
    }

}
