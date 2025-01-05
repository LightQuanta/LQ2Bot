package tech.lq0.plugins

import love.forte.simbot.component.onebot.v11.core.actor.OneBotMemberRole
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.message.At
import love.forte.simbot.message.messagesOf
import love.forte.simbot.message.toText
import love.forte.simbot.quantcat.common.annotations.ContentTrim
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import tech.lq0.interceptor.FunctionSwitch
import kotlin.time.Duration.Companion.minutes

@Component
class Mute {

    val timeRange = listOf(
        1, 1, 2, 2, 3, 3, 4, 5, 5,
        6, 6, 7, 7, 8, 8, 9, 9, 10,
        11, 12, 13, 14, 15, 17, 18, 19, 20,
        23, 25, 30, 40, 45, 50, 60, 70, 80,
        100, 114, 120, 180,
    )

    @Listener
    @FunctionSwitch("Mute")
    @ContentTrim
    @Filter("禁言抽奖")
    suspend fun OneBotNormalGroupMessageEvent.randomMute() {
        val time = timeRange.random()
        with(author()) {
            // 换用@应该就不用审核用户名了吧（）
            if (role != OneBotMemberRole.MEMBER) {
                content().send(
                    messagesOf(
                        "恭喜 ".toText(),
                        At(authorId),
                        " 抽中了1145141919810分钟禁言套餐！".toText(),
                    )
                )
            } else {
                ban(time.minutes)
                content().send(
                    messagesOf(
                        "恭喜 ".toText(),
                        At(authorId),
                        " 抽中了${time}分钟禁言套餐！".toText(),
                    )
                )
            }
        }
        // TODO 爬行者榜？
    }

    @Listener
    @FunctionSwitch("Mute")
    @ContentTrim
    @Filter("自助禁言 {{time,\\d{1,10}}}")
    suspend fun OneBotNormalGroupMessageEvent.customMute(@FilterValue("time") time: Int) {
        with(author()) {
            if (role != OneBotMemberRole.MEMBER) {
                content().send("在？有种把管理卸了")
            } else {
                val processedTime = time.coerceIn(1..720)
                ban(processedTime.minutes)
                // 换用@应该就不用审核用户名了吧（）
                content().send(
                    messagesOf(
                        "恭喜 ".toText(),
                        At(authorId),
                        " 成功领取了${processedTime}分钟的禁言套餐！".toText(),
                    )
                )
            }
        }
    }

}
