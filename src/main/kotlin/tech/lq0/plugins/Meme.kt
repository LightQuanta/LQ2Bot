package tech.lq0.plugins

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import love.forte.simbot.common.id.StringID.Companion.ID
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotFriendMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.message.toMessages
import love.forte.simbot.message.toText
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
    /**
     * setrep预设回复数据缓存
     * QQ号 -> (关键词, 预设回复)
     */

    val presetReplyInfo = mutableMapOf<String, Pair<String, String>>()

    @Listener
    @FunctionSwitch("Meme")
    @ChinesePunctuationReplace
    suspend fun OneBotMessageEvent.meme() {
        val text = this.messageContent.plainText
        if (text.isNullOrEmpty()) return

        // 预设回复处理
        if (authorId.toString() in presetReplyInfo) {
            val (keyword, reply) = presetReplyInfo[authorId.toString()]!!
            if (keyword == text) {
                directlySend(reply)
                presetReplyInfo.remove(authorId.toString())
                return
            }
        }

        memeConfig.memes.firstOrNull {
            listOf(it.name, *it.alias?.toTypedArray() ?: emptyArray())
                .any { keyword ->
                    when (it.detectType) {
                        DetectType.EQUAL -> text.lowercase() == keyword.lowercase()
                        DetectType.STARTS_WITH -> text.lowercase().startsWith(keyword.lowercase())
                        DetectType.REGEX_MATCH,
                        DetectType.REGEX_REPLACE -> text.matches(Regex(keyword, RegexOption.IGNORE_CASE))
                    }
                }
        }?.let {
            if (it.detectType != DetectType.REGEX_REPLACE) {
                // 普通回复默认已经经过审核，故不启用敏感词检测
                directlySend(it.replyContent.random())
            } else {
                // 为正则替换类型Meme进行敏感词检测
                if (text.isSensitive()) {
                    // directlySend("（测试消息）检测到违规内容")
                    val group = if (this is OneBotNormalGroupMessageEvent) this else null
                    banMember(authorId.toString(), group?.content())
                    return
                }

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
            val meme = findMemeInstance(keyword) ?: run {
                val tempMeme = SingleMeme(name = keyword)
                memeConfig.memes.add(tempMeme)
                tempMeme
            }
            meme.replyContent.addAll(replies)
            memeConfig.lastUpdateTime = Instant.now().epochSecond

            saveConfig("Meme", "meme.json", prettyJsonFormatter.encodeToString(memeConfig))
            directlySend("已更新$keyword")
        } else {
            notifyAdmin()
        }
    }

    @Listener
    @FunctionSwitch("Meme")
    @ChinesePunctuationReplace
    @Filter("addalias {{keyword,.+?}}#{{reply,.+}}")
    suspend fun OneBotMessageEvent.addAlias(
        @FilterValue("keyword") keyword: String,
        @FilterValue("reply") alias: String,
    ) {
        val aliases = alias.split("|")
        if (authorId.toString() in botPermissionConfig.admin || authorId.toString() in memeConfig.admin) {
            val meme = findMemeInstance(keyword) ?: return
            if (meme.alias == null) meme.alias = mutableSetOf()
            meme.alias?.addAll(aliases)
            memeConfig.lastUpdateTime = Instant.now().epochSecond

            saveConfig("Meme", "meme.json", prettyJsonFormatter.encodeToString(memeConfig))
            directlySend("已更新$keyword")
        } else {
            notifyAdmin()
        }
    }

    @Listener
    @FunctionSwitch("Meme")
    @ChinesePunctuationReplace
    @Filter("delmeme {{keyword,.+}}")
    suspend fun OneBotMessageEvent.delMeme(@FilterValue("keyword") keyword: String) {
        val meme = findMemeInstance(keyword) ?: return
        if (authorId.toString() in botPermissionConfig.admin || authorId.toString() in memeConfig.admin) {
            memeConfig.memes.remove(meme)
            memeConfig.lastUpdateTime = Instant.now().epochSecond
            saveConfig("Meme", "meme.json", prettyJsonFormatter.encodeToString(memeConfig))
            directlySend("已移除$keyword")
        } else {
            notifyAdmin()
        }
    }

    val prettyJsonFormatter = Json { prettyPrint = true }

    @Listener
    @FunctionSwitch("Meme")
    @ChinesePunctuationReplace
    @Filter("{{action,getmeme(json)?}} {{keyword,.+}}")
    suspend fun OneBotMessageEvent.getMeme(
        @FilterValue("action") action: String,
        @FilterValue("keyword") keyword: String,
    ) {
        val meme = findMemeInstance(keyword) ?: return
        if (action == "getmeme") {
            directlySend(meme.replyContent.joinToString("|"))
        } else {
            directlySend(prettyJsonFormatter.encodeToString(meme))
        }
    }

    @Listener
    @FunctionSwitch("Meme")
    @ChinesePunctuationReplace
    @Filter("setrep {{keyword,.+?}}#{{content,.+}}")
    suspend fun OneBotFriendMessageEvent.setReply(
        @FilterValue("keyword") keyword: String,
        @FilterValue("content") content: String,
    ) {
        val meme = findMemeInstance(keyword) ?: return
        val reply = meme.replyContent.firstOrNull { content in it }
        if (reply != null) {
            presetReplyInfo[authorId.toString()] = keyword to reply
            directlySend("已指定回复内容\n$keyword -> $reply")
        } else {
            directlySend("未发现该回复！")
        }
    }

    @Listener
    @FunctionSwitch("Meme")
    @ChinesePunctuationReplace
    @Filter("findmeme {{query,.+}}")
    suspend fun OneBotMessageEvent.findMeme(@FilterValue("query") query: String) {
        if (query.isEmpty()) return
        val keyword = query.takeWhile { it != '#' }
        val content = query.dropWhile { it != '#' }.drop(1)

        if (content.isNotEmpty()) {
            val meme = findMemeInstance(keyword) ?: return
            val replies = meme.replyContent.filter { content in it }
            if (replies.isNotEmpty()) {
                directlySend("查找到的回复：${replies.take(8).joinToString()}")
            } else {
                directlySend("未发现该回复！")
            }
        } else {
            val memes = memeConfig.memes.filter {
                setOf(it.name, *(it.alias?.toTypedArray() ?: emptyArray()))
                    .map { word -> word.lowercase() }
                    .any { word -> keyword.lowercase() in word }
            }
            if (memes.isNotEmpty()) {
                directlySend("查找到的关键词：${memes.take(8).joinToString { it.name }}")
            } else {
                directlySend("未发现该关键词！")
            }
        }
    }

    /**
     * 将Meme操纵请求转发给Meme管理员
     */
    suspend fun OneBotMessageEvent.notifyAdmin() {
        (memeConfig.notificationReceiver - authorId.toString())
            .forEach {
                val name = when (this) {
                    is OneBotNormalGroupMessageEvent -> author().name
                    is OneBotFriendMessageEvent -> content().name
                    else -> "未知用户"
                }
                bot.contactRelation.contact(it.ID)
                    ?.send(
                        listOf(
                            "来自$name(${authorId})的建议：\n".toText(), *messageContent.messages.toList().toTypedArray()
                        ).toMessages()
                    )
            }

        directlySend("已将建议转发给Bot管理员")
    }

    /**
     * 根据关键词查找有无对应Meme实例，未找到会回复相应提示
     */
    suspend fun OneBotMessageEvent.findMemeInstance(keyword: String): SingleMeme? =
        memeConfig.memes.firstOrNull {
            keyword.lowercase() in setOf(
                it.name,
                *(it.alias?.toTypedArray() ?: emptyArray())
            ).map { word -> word.lowercase() }
        } ?: run {
            directlySend("未发现该关键词！")
            return null
        }
}
