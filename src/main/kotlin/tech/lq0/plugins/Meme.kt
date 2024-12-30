package tech.lq0.plugins

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import love.forte.simbot.common.id.StringID.Companion.ID
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotFriendMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import tech.lq0.interceptor.ChinesePunctuationReplace
import tech.lq0.interceptor.FunctionSwitch
import tech.lq0.utils.*
import java.time.Instant

@Component
class Meme {

    @Listener
    @FunctionSwitch("Meme")
    @ChinesePunctuationReplace
    suspend fun OneBotMessageEvent.meme() {
        val text = this.messageContent.plainText
        if (text.isNullOrEmpty()) return

        memeConfig.memes.firstOrNull {
            listOf(it.name, *it.alias?.toTypedArray() ?: emptyArray())
                .any { keyword ->
                    when (it.detectType) {
                        DetectType.EQUAL -> text.lowercase() == keyword.lowercase()
                        DetectType.STARTS_WITH -> text.startsWith(keyword)
                        DetectType.REGEX_MATCH,
                        DetectType.REGEX_REPLACE -> text.matches(Regex(keyword, RegexOption.IGNORE_CASE))
                    }
                }
        }?.let {
            if (it.detectType != DetectType.REGEX_REPLACE) {
                directlySend(it.replyContent.random())
            } else {
                // TODO 敏感词检测
                val regex = Regex(it.name, RegexOption.IGNORE_CASE)
                val index = regex.matchEntire(text)
                    ?.groups
                    ?.get("id")
                    ?.value
                    ?.toIntOrNull()
                    ?.coerceIn(1..it.replyContent.size)

                directlySend(
                    regex.replace(
                        text,
                        if (index != null) {
                            it.replyContent.toList()[index - 1]
                        } else {
                            it.replyContent.random()
                        }
                    )
                )
            }
        }
    }

    @Listener
    @FunctionSwitch("Meme")
    @ChinesePunctuationReplace
    @Filter("addmeme {{keyword,.+}}#{{reply,.+}}")
    suspend fun OneBotMessageEvent.addMeme(
        @FilterValue("keyword") keyword: String,
        @FilterValue("reply") reply: String,
    ) {
        // TODO 图片添加支持
        val replies = reply.split("|")
        if (authorId.toString() in botPermissionConfig.admin || authorId.toString() in memeConfig.admin) {
            val meme =
                memeConfig.memes.firstOrNull { keyword in setOf(it.name, *(it.alias?.toTypedArray() ?: emptyArray())) }
                    ?: run {
                        val tempMeme = SingleMeme(name = keyword)
                        memeConfig.memes.add(tempMeme)
                        tempMeme
                    }
            meme.replyContent.addAll(replies)
            memeConfig.lastUpdateTime = Instant.now().epochSecond

            saveConfig("Meme", "meme.json", Json.encodeToString(memeConfig))
            directlySend("已更新$keyword")
        } else {
            (memeConfig.notificationReceiver - authorId.toString())
                .forEach {
                    val name = when (this) {
                        is OneBotNormalGroupMessageEvent -> author().name
                        is OneBotFriendMessageEvent -> content().name
                        else -> "未知用户"
                    }
                    bot.contactRelation.contact(it.ID)?.send("来自$name(${authorId})的建议：\naddmeme $keyword#$reply")
                }

            directlySend("已将建议转发给Bot管理员")
        }
    }

}
