package tech.lq0.plugins

import love.forte.simbot.component.onebot.v11.core.event.message.*
import love.forte.simbot.logger.LoggerFactory
import love.forte.simbot.message.safePlainText
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component

@Component
class ChatLogger {
    val logger = LoggerFactory.getLogger("CHAT")

    @Listener
    suspend fun OneBotMessageEvent.log() {
        // TODO 正确记录图片等消息
        when (this) {
            is OneBotGroupMessageEvent -> {
                val content = content()
                val authorInfo = when (this) {
                    is OneBotNormalGroupMessageEvent -> "${author().nick ?: author().name}($authorId)"
                    is OneBotAnonymousGroupMessageEvent -> "${author().nick ?: author().name}($authorId)"
                    else -> "系统消息"
                }
                logger.info("[${content.name}(${content.id})] $authorInfo -> ${messageContent.safePlainText}")
            }

            is OneBotFriendMessageEvent -> {
                val content = content()
                val authorInfo = "${content.name}$authorId"
                logger.info("$authorInfo -> ${messageContent.safePlainText}")
            }

            is OneBotGroupPrivateMessageEvent -> {
                val content = content()
                val authorInfo = "${content.name}$authorId"
                logger.info("$authorInfo -> ${messageContent.safePlainText}")
            }
        }
    }
}