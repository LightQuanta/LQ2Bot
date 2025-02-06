package tech.lq0.plugins

import love.forte.simbot.component.onebot.v11.core.actor.OneBotMember
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
import tech.lq0.utils.botPermissionConfig
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
        val target = getFirstAtOrAuthor() ?: return

        // 换用@应该就不用审核用户名了吧（）
        if (target.role != OneBotMemberRole.MEMBER) {
            content().send(
                messagesOf(
                    "恭喜 ".toText(),
                    At(authorId),
                    " 抽中了1145141919810分钟禁言套餐！".toText(),
                )
            )
        } else {
            target.ban(time.minutes)
            content().send(
                messagesOf(
                    "恭喜 ".toText(),
                    At(target.id),
                    " 抽中了${time}分钟禁言套餐！".toText(),
                )
            )
        }
    }

    @Listener
    @FunctionSwitch("Mute")
    @ContentTrim
    @Filter("自助禁言 {{time,\\d{1,10}}}")
    suspend fun OneBotNormalGroupMessageEvent.customMute(@FilterValue("time") time: Int) {
        val target = getFirstAtOrAuthor() ?: return

        if (target.role != OneBotMemberRole.MEMBER) {
            content().send("在？有种把管理卸了")
        } else {
            val processedTime = time.coerceIn(1..720)
            target.ban(processedTime.minutes)
            // 换用@应该就不用审核用户名了吧（）
            content().send(
                messagesOf(
                    "恭喜 ".toText(),
                    At(target.id),
                    " 成功领取了${processedTime}分钟的禁言套餐！".toText(),
                )
            )
        }
    }

    suspend fun OneBotNormalGroupMessageEvent.getFirstAtOrAuthor(): OneBotMember? {
        val messages = messageContent.messages.toList()
        val id = if (
            (author().role != OneBotMemberRole.MEMBER || authorId.toString() in botPermissionConfig.admin)
            && messages.filterIsInstance<At>().isNotEmpty()
        ) {
            // 允许管理员或群主借刀杀人
            messages.filterIsInstance<At>().first().target
        } else {
            return author()
        }
        return this.content().member(id)
    }

}
