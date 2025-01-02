package tech.lq0.utils

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.message.Messages
import love.forte.simbot.message.toText

/**
 * 直接发送消息，不会额外创建一条回复
 */
suspend fun OneBotMessageEvent.directlySend(message: String) =
    directlySend(love.forte.simbot.message.messagesOf(message.toText()))

/**
 * 直接发送消息，不会额外创建一条回复
 */
suspend fun OneBotMessageEvent.directlySend(message: Messages) {
    if (this is OneBotGroupMessageEvent) {
        content().send(message)
    } else {
        reply(message)
    }
}