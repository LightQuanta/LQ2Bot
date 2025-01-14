package tech.lq0.utils

import kotlinx.coroutines.*
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotFriendMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupPrivateMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.component.onebot.v11.message.OneBotMessageReceipt
import love.forte.simbot.component.onebot.v11.message.segment.OneBotText
import love.forte.simbot.message.*
import tech.lq0.interceptor.addMemberRateLimit
import kotlin.time.Duration.Companion.seconds

/**
 * 直接发送消息，不会额外创建一条回复
 */
suspend fun OneBotMessageEvent.directlySend(message: String, autoRevoke: Boolean = false) =
    directlySend(messagesOf(message.toText()), autoRevoke)

/**
 * 直接发送消息，不会额外创建一条回复
 */
suspend fun OneBotMessageEvent.directlySend(messages: Messages, autoRevoke: Boolean = false) {
    // 防止意外响应黑名单成员
    if (authorId.toString() in botPermissionConfig.memberBlackList) return
    // 为用户添加功能调用限流
    addMemberRateLimit(authorId.toString())

    // 是否在60s后自动撤回过长消息
    val shouldRevoke = autoRevoke && messages.toText().length > 200

    when (this) {
        is OneBotGroupMessageEvent -> {
            val content = content()
            // 防止意外响应黑名单群
            if (content.id.toString() in botPermissionConfig.groupDisabledList
                || content.id.toString() in botPermissionConfig.groupBlackList
            ) return

            content.send(messages).autoRevoke(shouldRevoke)
            chatLogger.info("bot <- 群 ${content.name}(${content.id}): ${messages.toText()}")
        }

        is OneBotGroupPrivateMessageEvent -> {
            val source = source()
            // 防止意外响应黑名单群
            if (source.id.toString() in botPermissionConfig.groupDisabledList
                || source.id.toString() in botPermissionConfig.groupBlackList
            ) return

            val content = content()
            reply(messages)
            chatLogger.info("bot <- 群 ${content.name}(${content.id}) ${content().nick ?: content().name}($authorId): ${messages.toText()}")
        }

        is OneBotFriendMessageEvent -> {
            val content = content()
            reply(messages)
            chatLogger.info("bot <- ${content.name}(${content.id}): ${messages.toText()}")
        }

        else -> {
            reply(messages).autoRevoke(shouldRevoke)
            chatLogger.info("bot <- ? : ${messages.toText()}")
        }
    }
}

val autoRevokeScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

/**
 * 在60s后自动撤回已发送消息
 * @param revoke 是否启用自动撤回
 */
private fun OneBotMessageReceipt.autoRevoke(revoke: Boolean) {
    if (!revoke) return

    autoRevokeScope.launch {
        delay(60.seconds)
        try {
            delete()
        } catch (e: Exception) {
            chatLogger.error("自动撤回失败: $e")
        }
    }
}

suspend fun OneBotMessageEvent.replyAndLog(message: String) =
    replyAndLog(messagesOf(message.toText()))

suspend fun OneBotMessageEvent.replyAndLog(messages: Messages) {
    // 防止意外响应黑名单成员
    if (authorId.toString() in botPermissionConfig.memberBlackList) return
    // 为用户添加功能调用限流
    addMemberRateLimit(authorId.toString())

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
fun Messages.toText() = toList().joinToString("") {
    when (it) {
        is At -> "[@${it.target}]"
        is AtAll -> "[@全体成员]"
        is Image -> "[图片]"
        is Text -> it.text

        // 说好的会自动转换成Text呢（
        is OneBotText.Element -> it.text

        else -> toString()
    }
}

/**
 * 将消息转换为文本
 */
fun MessageContent.toText() = messages.toText()