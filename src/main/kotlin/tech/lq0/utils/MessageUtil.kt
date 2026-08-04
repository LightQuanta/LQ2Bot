package tech.lq0.utils

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import love.forte.simbot.common.id.ID
import love.forte.simbot.component.onebot.v11.core.api.OneBotApi
import love.forte.simbot.component.onebot.v11.core.api.OneBotApiResult
import love.forte.simbot.component.onebot.v11.core.api.OneBotMessageOutgoing
import love.forte.simbot.component.onebot.v11.core.api.SendMsgResult
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotFriendMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupPrivateMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.component.onebot.v11.message.segment.OneBotAt
import love.forte.simbot.component.onebot.v11.message.segment.OneBotForwardNode
import love.forte.simbot.component.onebot.v11.message.segment.OneBotText
import love.forte.simbot.component.onebot.v11.message.segment.toElement
import love.forte.simbot.message.*
import tech.lq0.interceptor.addMemberRateLimit

/**
 * 直接发送消息，不会额外创建一条回复
 */
suspend fun OneBotMessageEvent.directlySend(message: String, foldMessageThreshold: Int = LONG_MESSAGE_LENGTH) =
    directlySend(messagesOf(message.toText()), foldMessageThreshold)

/**
 * 多长的消息会被视为过长消息
 */
const val LONG_MESSAGE_LENGTH = 500

/**
 * 直接发送消息，不会额外创建一条回复
 */
suspend fun OneBotMessageEvent.directlySend(messages: Messages, foldMessageThreshold: Int = LONG_MESSAGE_LENGTH) {
    // 防止意外响应黑名单成员
    val config = botPermissionConfig.get()
    if (authorId.toString() in config.memberBlackList) return
    // 为用户添加功能调用限流
    addMemberRateLimit(authorId.toString())

    // 是否将过长消息修改为转发消息
    val foldLongMessage = messages.toText().length > foldMessageThreshold

    when (this) {
        is OneBotGroupMessageEvent -> {
            val content = content()
            // 防止意外响应黑名单群
            if (content.id.toString() in config.groupDisabledList
                || content.id.toString() in config.groupBlackList
            ) return

            if (foldLongMessage) {
                bot.executeData(
                    SendForwardMsgApi.createGroup(
                        content.id,
                        OneBotMessageOutgoing.create(
                            listOf(OneBotForwardNode.create(bot.id, bot.name, messages.toOneBotMessage()))
                        )
                    )
                )

//                content.send(OneBotForwardNode.create(bot.id, bot.name, messages.toOneBotMessage()).toElement())
            } else {
                content.send(messages)
            }

            chatLogger.info("bot <- 群 ${content.name}(${content.id}): ${messages.toText()}")
        }

        is OneBotGroupPrivateMessageEvent -> {
            val source = source()
            // 防止意外响应黑名单群
            if (source.id.toString() in config.groupDisabledList
                || source.id.toString() in config.groupBlackList
            ) return

            val content = content()
            if (foldLongMessage) {
                reply(OneBotForwardNode.create(bot.id, bot.name, messages.toOneBotMessage()).toElement())
            } else {
                reply(messages)
            }
            chatLogger.info("bot <- 群 ${content.name}(${content.id}) ${content().nick ?: content().name}($authorId): ${messages.toText()}")
        }

        is OneBotFriendMessageEvent -> {
            val content = content()
            if (foldLongMessage) {
                bot.executeData(
                    SendForwardMsgApi.createPrivate(
                        content.id,
                        OneBotMessageOutgoing.create(
                            listOf(OneBotForwardNode.create(bot.id, bot.name, messages.toOneBotMessage()))
                        )
                    )
                )

//                reply(OneBotForwardNode.create(bot.id, bot.name, messages.toOneBotMessage()).toElement())
            } else {
                reply(messages)
            }
            chatLogger.info("bot <- ${content.name}(${content.id}): ${messages.toText()}")
        }

        else -> {
            if (foldLongMessage) {
                reply(OneBotForwardNode.create(bot.id, bot.name, messages.toOneBotMessage()).toElement())
            } else {
                reply(messages)
            }
            chatLogger.info("bot <- ? : ${messages.toText()}")
        }
    }
}

/**
 * 转发消息处理，修改自SendMsgApi
 */
class SendForwardMsgApi private constructor(
    override val body: Body,
) : OneBotApi<SendMsgResult> {
    override val action: String
        get() = if (body.messageType == MESSAGE_TYPE_PRIVATE) "send_private_forward_msg" else "send_group_forward_msg"

    override val resultDeserializer: DeserializationStrategy<SendMsgResult>
        get() = SendMsgResult.serializer()

    override val apiResultDeserializer: DeserializationStrategy<OneBotApiResult<SendMsgResult>>
        get() = RES_SER

    companion object Factory {
        const val MESSAGE_TYPE_PRIVATE: String = "private"
        const val MESSAGE_TYPE_GROUP: String = "group"

        private val RES_SER: KSerializer<OneBotApiResult<SendMsgResult>> =
            OneBotApiResult.serializer(SendMsgResult.serializer())

        /**
         * 构建一个 [SendForwardMsgApi].
         *
         * @param messageType 消息类型，支持 `private`、`group`，分别对应私聊、群组，如不传入，则根据传入的 `*_id` 参数判断
         * @param userId 对方 QQ 号（消息类型为 `private` 时需要）
         * @param groupId 群号（消息类型为 `group` 时需要）
         * @param message 要发送的内容
         * @param autoEscape 消息内容是否作为纯文本发送（即不解析 CQ 码），只在 `message` 字段是字符串时有效
         */
        @JvmStatic
        @JvmOverloads
        fun create(
            messageType: String,
            userId: ID?,
            groupId: ID?,
            message: OneBotMessageOutgoing,
            autoEscape: Boolean? = null,
        ): SendForwardMsgApi = SendForwardMsgApi(Body(messageType, userId, groupId, message, autoEscape))

        /**
         * 构建一个发送到私聊的 [SendForwardMsgApi].
         *
         * @param userId 对方 QQ 号（消息类型为 `private` 时需要）
         * @param message 要发送的内容
         * @param autoEscape 消息内容是否作为纯文本发送（即不解析 CQ 码），只在 `message` 字段是字符串时有效
         */
        @JvmStatic
        @JvmOverloads
        fun createPrivate(
            userId: ID,
            message: OneBotMessageOutgoing,
            autoEscape: Boolean? = null,
        ): SendForwardMsgApi = create(
            messageType = MESSAGE_TYPE_PRIVATE,
            userId = userId,
            groupId = null,
            message = message,
            autoEscape = autoEscape
        )

        /**
         * 构建一个发送到群聊的 [SendForwardMsgApi].
         *
         * @param groupId 群号（消息类型为 `group` 时需要）
         * @param message 要发送的内容
         * @param autoEscape 消息内容是否作为纯文本发送（即不解析 CQ 码），只在 `message` 字段是字符串时有效
         */
        @JvmStatic
        @JvmOverloads
        fun createGroup(
            groupId: ID,
            message: OneBotMessageOutgoing,
            autoEscape: Boolean? = null,
        ): SendForwardMsgApi = create(
            messageType = MESSAGE_TYPE_GROUP,
            userId = null,
            groupId = groupId,
            message = message,
            autoEscape = autoEscape
        )
    }

    /**
     * @property messageType 消息类型，支持 `private`、`group`，分别对应私聊、群组，如不传入，则根据传入的 `*_id` 参数判断
     * @property userId 对方 QQ 号（消息类型为 `private` 时需要）
     * @property groupId 群号（消息类型为 `group` 时需要）
     * @property message 要发送的内容
     * @property autoEscape 消息内容是否作为纯文本发送（即不解析 CQ 码），只在 `message` 字段是字符串时有效
     */
    @Serializable
    data class Body(
        @SerialName("message_type")
        internal val messageType: String,
        @SerialName("user_id")
        internal val userId: ID? = null,
        @SerialName("group_id")
        internal val groupId: ID? = null,
        internal val message: OneBotMessageOutgoing,
        @SerialName("auto_escape")
        internal val autoEscape: Boolean? = null,
    )
}


suspend fun OneBotMessageEvent.replyAndLog(message: String) =
    replyAndLog(messagesOf(message.toText()))

suspend fun OneBotMessageEvent.replyAndLog(messages: Messages) {
    // 防止意外响应黑名单成员
    val config = botPermissionConfig.get()
    if (authorId.toString() in config.memberBlackList) return
    // 为用户添加功能调用限流
    addMemberRateLimit(authorId.toString())

    when (this) {
        is OneBotGroupMessageEvent -> {
            val content = content()
            // 防止意外响应黑名单群
            if (content.id.toString() in config.groupDisabledList
                || content.id.toString() in config.groupBlackList
            ) return

            chatLogger.info("bot <- 群 ${content.name}(${content.id}): ${messages.toText()}")
        }

        is OneBotGroupPrivateMessageEvent -> {
            val source = source()
            // 防止意外响应黑名单群
            if (source.id.toString() in config.groupDisabledList
                || source.id.toString() in config.groupBlackList
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
 * 去除@后将消息转为文本
 */
fun Messages.toTextWithoutAt() = toList().filter { it !is At }.toText()

/**
 * 将消息转换为文本
 */
fun Messages.toText() = toList().toText()

fun List<Message.Element>.toText() = joinToString("") {
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

/**
 * 将消息转换为List<OneBotMessage>
 */
fun Messages.toOneBotMessage() = toList().map {
    when (it) {
        is At -> OneBotAt.create(it.target)
        is Text -> OneBotText.create(it.text)
        is OneBotText.Element -> OneBotText.create(it.text)

        // 有必要支持其他类型吗）
        else -> OneBotText.create("")
    }
}