package tech.lq0.utils

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent

/**
 * 在群聊里直接发送消息，而不是发送一条回复
 */
suspend fun OneBotMessageEvent.directlySend(message: String) {
    if (this is OneBotGroupMessageEvent) {
        content().send(message)
    } else {
        reply(message)
    }
}