package tech.lq0.utils

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotFriendMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupPrivateMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.message.*

/**
 * 直接发送消息，不会额外创建一条回复
 */
suspend fun OneBotMessageEvent.directlySend(message: String) =
    directlySend(messagesOf(message.toText()))

/**
 * 直接发送消息，不会额外创建一条回复
 */
suspend fun OneBotMessageEvent.directlySend(messages: Messages) {
    // 防止意外响应黑名单成员
    if (authorId.toString() in botPermissionConfig.memberBlackList) return

    when (this) {
        is OneBotGroupMessageEvent -> {
            val content = content()

            // 防止意外响应黑名单群
            if (content.id.toString() in botPermissionConfig.groupDisabledList
                || content.id.toString() in botPermissionConfig.groupBlackList
            ) return

            content.send(messages)
            chatLogger.info("bot <- 群 ${content.name}(${content.id}): ${messages.toText()}")
        }

        is OneBotGroupPrivateMessageEvent -> {
            val source = source()

            // 防止意外响应黑名单群
            if (source.id.toString() in botPermissionConfig.groupDisabledList
                || source.id.toString() in botPermissionConfig.groupBlackList
            ) return

            source.send(messages)
            chatLogger.info("bot <- 群 ${source.name}(${source.id}) ${content().nick ?: content().name}($authorId): ${messages.toText()}")
        }

        is OneBotFriendMessageEvent -> {
            val content = content()
            reply(messages)
            chatLogger.info("bot <- ${content.name}(${content.id}): ${messages.toText()}")
        }

        else -> {
            reply(messages)
            chatLogger.info("bot <- ? : ${messages.toText()}")
        }
    }
}

suspend fun OneBotMessageEvent.replyAndLog(message: String) =
    replyAndLog(messagesOf(message.toText()))

suspend fun OneBotMessageEvent.replyAndLog(messages: Messages) {
    // 防止意外响应黑名单成员
    if (authorId.toString() in botPermissionConfig.memberBlackList) return

    when (this) {
        is OneBotGroupMessageEvent -> {
            val content = content()

            // 防止意外响应黑名单群
            if (content.id.toString() in botPermissionConfig.groupDisabledList
                || content.id.toString() in botPermissionConfig.groupBlackList
            ) return

            chatLogger.info("bot <- 群 ${content.name}(${content.id}): ${messages.toText()}")
        }

        is OneBotGroupPrivateMessageEvent -> {
            val source = source()

            // 防止意外响应黑名单群
            if (source.id.toString() in botPermissionConfig.groupDisabledList
                || source.id.toString() in botPermissionConfig.groupBlackList
            ) return

            chatLogger.info("bot <- ${source.name}(${source.id}) ${content().nick ?: content().name}($authorId): ${messages.toText()}")
        }

        is OneBotFriendMessageEvent -> {
            val content = content()
            chatLogger.info("bot <- ${content.name}(${content.id}): ${messages.toText()}")
        }

        else -> chatLogger.info("bot <- ? : ${messages.toText()}")
    }
    reply(messages)
}

/**
 * 将消息转换为文本
 */
fun Messages.toText() = joinToString("") {
    when (it) {
        is At -> "[@${it.target}]"
        is AtAll -> "[@全体成员]"
        is Image -> "[图片]"
        is Text -> it.text
        else -> toString()
    }
}

/**
 * 将消息转换为文本
 */
fun MessageContent.toText() = messages.toText()