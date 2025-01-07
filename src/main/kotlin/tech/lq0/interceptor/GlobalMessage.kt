package tech.lq0.interceptor

import love.forte.simbot.application.Application
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.event.EventResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import tech.lq0.utils.botPermissionConfig
import tech.lq0.utils.chatLogger

/**
 * Bot功能调用限流记录
 */
val memberRateLimit: MutableMap<String, Pair<Long, Int>> = mutableMapOf()

/**
 * 为Bot功能调用添加限流
 * @param memberId 用户QQ号
 */
fun addMemberRateLimit(memberId: String) {
    // 不限制Bot管理员
    if (memberId in botPermissionConfig.admin) return

    val currentTimeStamp = System.currentTimeMillis()
    if (memberId in memberRateLimit) {
        val (time, count) = memberRateLimit[memberId]!!

        if (currentTimeStamp > time) {
            // 超过原先30s则重置限制
            memberRateLimit[memberId] = (currentTimeStamp + 30 * 1000) to 1
        } else {
            // 更新30s内发言次数
            memberRateLimit[memberId] = time to (count + 1)
        }
    } else {
        // 未记录则初始化
        memberRateLimit[memberId] = (currentTimeStamp + 30 * 1000) to 1
    }
}

@Component
class GlobalMessage @Autowired constructor(app: Application) {
    init {
        app.eventDispatcher.register {
            val event = this.event
            if (event !is OneBotMessageEvent) return@register EventResult.empty()

            // 不拦截Bot管理员的消息
            if (event.authorId.toString() in botPermissionConfig.admin) {
                return@register EventResult.empty()
            }

            // 忽视被拉黑用户和群聊的任何消息
            if (event.authorId.toString() in botPermissionConfig.memberBlackList) {
                return@register EventResult.empty(true)
            }
            if (
                event is OneBotGroupMessageEvent
                && event.groupId.toString() in botPermissionConfig.groupBlackList
            ) return@register EventResult.empty(true)

            // 发言限流
            if (event.authorId.toString() in memberRateLimit) {
                val (time, count) = memberRateLimit[event.authorId.toString()]!!
                // 记录超时则不进行处理
                if (System.currentTimeMillis() > time) return@register EventResult.empty()

                // 30s内调用Bot功能超过10次开始限流
                if (count >= 10) {
                    chatLogger.warn("用户 ${event.authorId} 在30s内调用Bot功能超过10次，已限制其发言")
                    return@register EventResult.empty(true)
                }
            }

            return@register EventResult.empty()
        }
    }

}