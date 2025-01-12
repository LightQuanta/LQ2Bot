package tech.lq0.plugins

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import love.forte.simbot.common.id.StringID.Companion.ID
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotFriendMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.message.OfflineImage.Companion.toOfflineImage
import love.forte.simbot.message.messagesOf
import love.forte.simbot.message.toMessages
import love.forte.simbot.message.toText
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import love.forte.simbot.resource.toResource
import org.springframework.stereotype.Component
import tech.lq0.interceptor.FunctionSwitch
import tech.lq0.interceptor.RequireAdmin
import tech.lq0.utils.*
import java.time.Instant
import kotlin.io.path.Path

@Component
class Meme {

    /**
     * setrep预设回复数据缓存
     * QQ号 -> (关键词, 预设回复)
     */

    val presetReplyInfo = mutableMapOf<String, Pair<String, String>>()

    @Listener
    @FunctionSwitch("Meme")
    suspend fun OneBotMessageEvent.meme() {
        val text = this.messageContent.plainText?.trim()
        if (text.isNullOrEmpty()) return

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
            // 群黑/白名单处理
            if (this is OneBotGroupMessageEvent) {
                if (!it.whiteListGroups.isNullOrEmpty() && groupId.toString() !in it.whiteListGroups
                    || groupId.toString() in (it.blackListGroups ?: setOf())
                ) return
            }

            // 预设回复处理
            if (authorId.toString() in presetReplyInfo) {
                val (keyword, reply) = presetReplyInfo[authorId.toString()]!!
                if (keyword == text) {
                    directlySend(reply)
                    presetReplyInfo.remove(authorId.toString())
                    return
                }
            }

            if (it.detectType != DetectType.REGEX_REPLACE) {
                // 普通回复默认已经经过审核，故不启用敏感词检测
                val reply = it.replyContent.random()

                if (reply.startsWith("[picture]")) {
                    val imageName = reply.substringAfter("[picture]")
                    try {
                        directlySend(
                            messagesOf(
                                Path("./lq2bot/config/Meme/images/$imageName").toResource().toOfflineImage()
                            )
                        )
                    } catch (e: Exception) {
                        memeLogger.error("发送图片 $imageName 失败: $e")
                        directlySend(imageName)
                    }
                } else {
                    directlySend(reply)
                }

            } else {
                // 为正则替换类型Meme进行敏感词检测
                if (text.isSensitive()) {
                    // directlySend("（测试消息）检测到违规内容")
                    val group = if (this is OneBotNormalGroupMessageEvent) this else null
                    banMember(authorId.toString(), group?.content())
                    return
                }

                val regex = Regex(it.name, RegexOption.IGNORE_CASE)
                val index = runCatching {
                    regex.matchEntire(text)
                        ?.groups
                        ?.get("id")
                        ?.value
                        ?.toIntOrNull()
                        ?.coerceIn(1..it.replyContent.size)
                }.getOrNull()

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

    suspend fun OneBotMessageEvent.logUpdate() {
        val name = when (this) {
            is OneBotNormalGroupMessageEvent -> author().name
            is OneBotFriendMessageEvent -> content().name
            else -> "未知用户"
        }
        val groupInfo = when (this) {
            is OneBotNormalGroupMessageEvent -> "在群${content().name}(${content().id})中"
            else -> ""
        }
        memeLogger.info("管理员$name($authorId)${groupInfo}进行了更新操作: ${messageContent.plainText}")
    }

    @Listener
    @FunctionSwitch("Meme")
    @Filter("addmeme {{keyword,.+}}#{{reply,.+}}")
    suspend fun OneBotMessageEvent.addMeme(
        @FilterValue("keyword") keyword: String,
        @FilterValue("reply") reply: String,
    ) {
        // TODO 图片添加支持
        val replies = reply.split("|").filter { it.isNotEmpty() }.map { it.trim() }
        if (authorId.toString() in botPermissionConfig.admin || authorId.toString() in memeConfig.admin) {
            val meme = findMemeInstance(keyword, false) ?: run {
                val tempMeme = SingleMeme(name = keyword)
                memeConfig.memes.add(tempMeme)
                tempMeme
            }
            meme.replyContent.addAll(replies)
            memeConfig.lastUpdateTime = Instant.now().epochSecond

            saveConfig("Meme", "meme.json", prettyJsonFormatter.encodeToString(memeConfig))
            logUpdate()
            directlySend("已更新$keyword")
        } else {
            notifyAdmin()
        }
    }

    @Listener
    @FunctionSwitch("Meme")
    @Filter("addalias {{keyword,.+?}}#{{alias,.+}}")
    suspend fun OneBotMessageEvent.addAlias(
        @FilterValue("keyword") keyword: String,
        @FilterValue("alias") alias: String,
    ) {
        val aliases = alias.split("|").filter { it.isNotEmpty() }.map { it.trim().lowercase() }
        if (authorId.toString() in botPermissionConfig.admin || authorId.toString() in memeConfig.admin) {
            val meme = findMemeInstance(keyword) ?: return
            if (meme.alias == null) meme.alias = mutableSetOf()
            meme.alias!!.addAll(aliases)

            memeConfig.lastUpdateTime = Instant.now().epochSecond
            saveConfig("Meme", "meme.json", prettyJsonFormatter.encodeToString(memeConfig))
            logUpdate()
            directlySend("已更新$keyword")
        } else {
            notifyAdmin()
        }
    }

    @Listener
    @FunctionSwitch("Meme")
    @Filter("delmeme {{operation,.+}}")
    suspend fun OneBotMessageEvent.delMeme(@FilterValue("operation") operation: String) {
        val keyword = operation.substringBefore("#").trim()
        val replies = operation.substringAfter("#", "")
            .split("|")
            .filter { it.isNotEmpty() }
            .map { it.trim() }
            .toSet()

        val meme = findMemeInstance(keyword) ?: return
        if (replies.isNotEmpty() && replies.none { it in meme.replyContent }) {
            directlySend("在 $keyword 中没有找到该回复！")
            return
        }

        if (authorId.toString() in botPermissionConfig.admin || authorId.toString() in memeConfig.admin) {
            if (replies.isNotEmpty()) {
                val removed = meme.replyContent intersect replies
                meme.replyContent -= replies

                directlySend("已移除${keyword}中的以下${removed.size}个回复:\n${removed.joinToString()}")
            } else {
                memeConfig.memes -= meme
                directlySend("已移除$keyword")
            }

            memeConfig.lastUpdateTime = Instant.now().epochSecond
            saveConfig("Meme", "meme.json", prettyJsonFormatter.encodeToString(memeConfig))
            logUpdate()
        } else {
            notifyAdmin()
        }
    }

    @Listener
    @RequireAdmin
    @FunctionSwitch("Meme")
    @Filter("{{operation,(un)?}}banmeme {{keyword,.+}}")
    suspend fun OneBotNormalGroupMessageEvent.banMeme(
        @FilterValue("operation") operation: String,
        @FilterValue("keyword") keyword: String
    ) {
        val meme = findMemeInstance(keyword) ?: return

        if (operation == "un") {
            if (meme.blackListGroups != null && groupId.toString() in meme.blackListGroups!!) {
                meme.blackListGroups!! -= groupId.toString()
                directlySend("已在该群重新启用 $keyword")
            } else {
                directlySend("该群没有禁用 $keyword ！")
                return
            }
        } else {
            if (meme.blackListGroups == null) meme.blackListGroups = mutableSetOf()
            meme.blackListGroups!! += groupId.toString()
            directlySend("已在该群禁用 $keyword")
        }

        memeConfig.lastUpdateTime = Instant.now().epochSecond
        saveConfig("Meme", "meme.json", prettyJsonFormatter.encodeToString(memeConfig))
        logUpdate()
    }

    val prettyJsonFormatter = Json { prettyPrint = true }

    @Listener
    @FunctionSwitch("Meme")
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
    @Filter("findmeme {{query,.+}}")
    suspend fun OneBotMessageEvent.findMeme(@FilterValue("query") query: String) {
        if (query.isEmpty()) return
        val keyword = query.substringBefore("#").trim().lowercase()
        val content = query.substringAfter("#", "").trim()

        if (content.isNotEmpty()) {
            val meme = findMemeInstance(keyword) ?: return
            val replies = meme.replyContent.filter { content in it }
            if (replies.isNotEmpty()) {
                directlySend("查找到的回复: ${replies.take(8).joinToString()}")
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
                directlySend("查找到的关键词: ${memes.take(8).joinToString { it.name }}")
            } else {
                directlySend("未发现该关键词！")
            }
        }
    }

    /**
     * 将Meme操纵请求转发给Meme管理员
     */
    suspend fun OneBotMessageEvent.notifyAdmin() {
        val name = when (this) {
            is OneBotNormalGroupMessageEvent -> author().name
            is OneBotFriendMessageEvent -> content().name
            else -> "未知用户"
        }
        val groupInfo = when (this) {
            is OneBotNormalGroupMessageEvent -> "群${content().id}(${content().name})的"
            else -> ""
        }
        memeLogger.info("来自$groupInfo$name($authorId)的建议: ${messageContent.plainText}")

        (memeConfig.notificationReceiver - authorId.toString())
            .forEach {
                bot.contactRelation.contact(it.ID)
                    ?.send(
                        listOf(
                            "来自$groupInfo$name(${authorId})的建议: \n".toText(),
                            *messageContent.messages.toList().toTypedArray()
                        ).toMessages()
                    )
            }

        directlySend("已将建议转发给Bot管理员")
    }

    /**
     * 根据关键词查找有无对应Meme实例，未找到会回复相应提示
     */
    suspend fun OneBotMessageEvent.findMemeInstance(keyword: String, sendFeedback: Boolean = true): SingleMeme? =
        memeConfig.memes.firstOrNull {
            keyword.trim().lowercase() in setOf(
                it.name,
                *(it.alias?.toTypedArray() ?: emptyArray())
            ).map { word -> word.lowercase() }
        } ?: run {
            if (sendFeedback) {
                directlySend("未发现该关键词！")
            }
            return null
        }
}
