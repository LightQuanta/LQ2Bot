package tech.lq0.interceptor

import love.forte.simbot.application.Application
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.event.EventResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import tech.lq0.utils.botPermissionConfig

@Component
class GlobalMessage @Autowired constructor(app: Application) {
    init {
        app.eventDispatcher.register {
            val event = this.event
            if (event !is OneBotMessageEvent) return@register EventResult.empty()

            // 忽视被拉黑用户和群聊的任何消息
            if (event.authorId.toString() in botPermissionConfig.memberBlackList) {
                return@register EventResult.empty(true)
            }
            if (
                event is OneBotGroupMessageEvent
                && event.groupId.toString() in botPermissionConfig.groupBlackList
            ) return@register EventResult.empty(true)

            return@register EventResult.empty()
        }
    }

}