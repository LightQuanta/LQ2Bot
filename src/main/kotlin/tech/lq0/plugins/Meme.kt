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
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes

@Component
class Meme {

    /**
     * 匹配时间超过100ms视为超时的正则匹配，避免灾难性回溯
     */
    fun testRegexWithTimeout(pattern: String, input: String, matchEntire: Boolean): Boolean {
        val executor = Executors.newSingleThreadExecutor()
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val future = executor.submit<Boolean> {
            if (matchEntire) {
                input.matches(regex)
            } else {
                regex.containsMatchIn(input)
            }
        }
        return try {
            future.get(100, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            false
        } finally {
            executor.shutdownNow()
        }
    }


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

        val groupID = if (this is OneBotGroupMessageEvent) groupId.toString() else null
        memeConfig.memes.filter { it.availableTo(groupID) }.firstOrNull {
            listOf(it.name, *it.alias.toTypedArray())
                .any { keyword ->
                    when (it.detectType) {
                        DetectType.EQUAL -> text.lowercase() == keyword.lowercase()
                        DetectType.STARTS_WITH -> text.lowercase().startsWith(keyword.lowercase())
                        DetectType.REGEX_MATCH,
                        DetectType.REGEX_REPLACE -> testRegexWithTimeout(
                            keyword,
                            text,
                            it.detectType == DetectType.REGEX_REPLACE
                        )
                    }
                }
        }?.let {
            // 避免处理空回复内容Meme
            if (it.replyContent.isEmpty()) return

            // 预设回复处理
            if (authorId.toString() in presetReplyInfo) {
                val (keyword, reply) = presetReplyInfo[authorId.toString()]!!
                if (keyword == text) {
                    directlySend(reply, true)
                    presetReplyInfo.remove(authorId.toString())
                    return
                }
            }

            if (it.detectType != DetectType.REGEX_REPLACE) {
                // 普通回复默认已经经过审核，故不启用敏感词检测
                val reply = it.replyContent.random()

                if (reply.startsWith("[picture]")) {
                    // 图片回复
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
                } else if (reply.startsWith("[ban]") && this is OneBotNormalGroupMessageEvent) {
                    // 群内自动禁言
                    val time = reply.substringAfter("[ban]").toIntOrNull()?.coerceIn(1..1440)
                    time?.let {
                        try {
                            author().ban(time.minutes)
                        } catch (e: Exception) {
                            chatLogger.error("禁言失败: $e")
                            directlySend(reply, true)
                        }
                    }
                } else {
                    // 普通回复
                    directlySend(reply, true)
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
                    regex.findAll(text)
                        .firstOrNull { match -> match.groups["id"] != null }
                        ?.groups
                        ?.get("id")
                        ?.value
                        ?.toIntOrNull()
                        ?.coerceIn(1..it.replyContent.size)
                }.getOrNull()

                directlySend(
                    regex.replaceFirst(
                        text,
                        if (index != null) {
                            it.replyContent.toList()[index - 1]
                        } else {
                            it.replyContent.random()
                        }
                    ),
                    true,
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
    @Filter("{{operation,add(group)?(meme|regexmeme|startswithmeme|regexreplacememe)}} {{keyword,.+}}#{{reply,.+}}")
    suspend fun OneBotMessageEvent.addMeme(
        @FilterValue("operation") operation: String,
        @FilterValue("keyword") keyword: String,
        @FilterValue("reply") reply: String,
    ) {
        // TODO 图片添加支持
        val replies = reply.split("|").filter { it.isNotEmpty() }.map { it.trim() }

        val isGroupMeme = operation.startsWith("addgroup")
        if (isGroupMeme && this !is OneBotNormalGroupMessageEvent) return

        val type = if (isGroupMeme) operation.substringAfter("addgroup") else operation.substringAfter("add")
        val detectType = when (type) {
            "meme" -> DetectType.EQUAL
            "regexmeme" -> DetectType.REGEX_MATCH
            "startswithmeme" -> DetectType.STARTS_WITH
            "regexreplacememe" -> DetectType.REGEX_REPLACE
            else -> DetectType.EQUAL
        }

        if (detectType == DetectType.REGEX_REPLACE || detectType == DetectType.REGEX_MATCH) {
            try {
                Regex(keyword)
            } catch (e: Exception) {
                directlySend("正则表达式输入格式错误！")
                return
            }
        }

        if (authorId.toString() in botPermissionConfig.admin || authorId.toString() in memeConfig.admin) {
            val groupID = if (this is OneBotNormalGroupMessageEvent) groupId.toString() else null

            val isUUID = try {
                UUID.fromString(keyword)
                true
            } catch (_: Exception) {
                false
            }

            val meme = if (isGroupMeme) {
                findMemeInstance(keyword, false) {
                    whiteListGroups.isNotEmpty() && groupID in whiteListGroups
                } ?: if (isUUID) {
                    directlySend("该Meme不存在！")
                    return
                } else {
                    SingleMeme(
                        name = keyword,
                        detectType = detectType,
                        whiteListGroups = mutableSetOf(groupID!!)
                    ).also { memeConfig.memes += it }
                }
            } else {
                findMemeInstance(keyword, false)
                    ?: if (isUUID) {
                        directlySend("该Meme不存在！")
                        return
                    } else {
                        SingleMeme(name = keyword, detectType = detectType).also { memeConfig.memes += it }
                    }
            }

            meme.replyContent.addAll(replies)
            memeConfig.lastUpdateTime = Instant.now().epochSecond

            saveConfig("Meme", "meme.json", prettyJsonFormatter.encodeToString(memeConfig))
            logUpdate()
            directlySend("已更新${meme.name}(id: ${meme.id})")
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
            meme.alias.addAll(aliases)

            memeConfig.lastUpdateTime = Instant.now().epochSecond
            saveConfig("Meme", "meme.json", prettyJsonFormatter.encodeToString(memeConfig))
            logUpdate()
            directlySend("已更新${meme.name}(id: ${meme.id})")
        } else {
            notifyAdmin()
        }
    }

    @Listener
    @FunctionSwitch("Meme")
    @Filter("modifymeme {{keyword,\\S+?}} {{operation,\\w+}} {{args,.+}}")
    suspend fun OneBotMessageEvent.modifyMeme(
        @FilterValue("keyword") keyword: String,
        @FilterValue("operation") operation: String,
        @FilterValue("args") args: String,
    ) {
        if (args.isBlank()) return
        if (authorId.toString() in botPermissionConfig.admin || authorId.toString() in memeConfig.admin) {
            val meme = findMemeInstance(keyword) ?: return

            when (operation) {
                "addwhitelist" -> {
                    val groups = args.split(Regex("\\S+")).mapNotNull { it.toIntOrNull()?.toString() }
                    meme.whiteListGroups.addAll(groups)
                }

                "removewhitelist" -> {
                    val groups = args.split(Regex("\\S+")).mapNotNull { it.toIntOrNull()?.toString() }
                    meme.whiteListGroups.removeAll(groups.toSet())
                }

                "addblacklist" -> {
                    val groups = args.split(Regex("\\S+")).mapNotNull { it.toIntOrNull()?.toString() }
                    meme.blackListGroups.addAll(groups)
                }

                "removeblacklist" -> {
                    val groups = args.split(Regex("\\S+")).mapNotNull { it.toIntOrNull()?.toString() }
                    meme.blackListGroups.removeAll(groups.toSet())
                }

                "rename" -> {
                    meme.alias -= args
                    meme.name = args
                }

                "modifytype" -> {
                    meme.detectType = when (args) {
                        "equal" -> DetectType.EQUAL
                        "regex" -> DetectType.REGEX_MATCH
                        "regexreplace" -> DetectType.REGEX_REPLACE
                        "startswith" -> DetectType.STARTS_WITH
                        else -> {
                            directlySend("请输入正确的检测类型！")
                            return
                        }
                    }
                }

                else -> {
                    directlySend("请输入正确的操作类型！")
                    return
                }
            }

            memeConfig.lastUpdateTime = Instant.now().epochSecond
            saveConfig("Meme", "meme.json", prettyJsonFormatter.encodeToString(memeConfig))
            logUpdate()
            directlySend("已更新${meme.name}(id: ${meme.id})")
        } else {
            notifyAdmin()
        }
    }

    @Listener
    @FunctionSwitch("Meme")
    @Filter("del{{type,(group)?meme}} {{operation,.+}}")
    suspend fun OneBotMessageEvent.delMeme(
        @FilterValue("type") type: String,
        @FilterValue("operation") operation: String,
    ) {
        val keyword = operation.substringBefore("#").trim()
        val replies = operation.substringAfter("#", "")
            .split("|")
            .filter { it.isNotEmpty() }
            .map { it.trim() }
            .toSet()
        val isGroup = type.startsWith("group")
        if (isGroup && this !is OneBotNormalGroupMessageEvent) return
        val groupID = if (this is OneBotNormalGroupMessageEvent) groupId.toString() else null

        val meme = findMemeInstance(keyword) {
            if (isGroup) {
                whiteListGroups.isNotEmpty() && groupID in whiteListGroups
            } else {
                true
            }
        } ?: return
        if (replies.isNotEmpty() && replies.none { it in meme.replyContent }) {
            directlySend("在 ${meme.name} 中没有找到该回复！")
            return
        }

        if (authorId.toString() in botPermissionConfig.admin || authorId.toString() in memeConfig.admin) {
            if (replies.isNotEmpty()) {
                val removed = meme.replyContent intersect replies
                meme.replyContent -= replies

                directlySend("已移除${meme.name}(id: ${meme.id})中的以下${removed.size}个回复:\n${removed.joinToString()}")
            } else {
                memeConfig.memes -= meme
                directlySend("已移除${meme.name}(id: ${meme.id})")
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
        @FilterValue("keyword") keyword: String,
    ) {
        val meme = findMemeInstance(keyword) ?: return

        if (operation == "un") {
            if (groupId.toString() in meme.blackListGroups) {
                meme.blackListGroups -= groupId.toString()
                directlySend("已在该群重新启用 ${meme.name}(id: ${meme.id})")
            } else {
                directlySend("该群没有禁用 ${meme.name}(id: ${meme.id}) ！")
                return
            }
        } else {
            meme.blackListGroups += groupId.toString()
            directlySend("已在该群禁用 ${meme.name}(id: ${meme.id})")
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
            val isAdmin = authorId.toString() in botPermissionConfig.admin || authorId.toString() in memeConfig.admin
            directlySend(
                prettyJsonFormatter.encodeToString(
                    if (isAdmin) {
                        meme
                    } else {
                        meme.copy(whiteListGroups = mutableSetOf(), blackListGroups = mutableSetOf())
                    }
                )
            )
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

        val memes = findMemeInstances(keyword, content.isEmpty()) {
            if (authorId.toString() in botPermissionConfig.admin || authorId.toString() in memeConfig.admin) {
                // 对管理员始终返回所有Meme
                true
            } else if (this@findMeme is OneBotNormalGroupMessageEvent) {
                // 群里使用仅返回公用或该群为白名单成员的Meme
                whiteListGroups.isEmpty() || groupId.toString() in whiteListGroups
            } else {
                // 普通用户私聊使用返回所有无白名单群的Meme
                whiteListGroups.isEmpty()
            }
        }

        if (memes.isEmpty()) {
            directlySend("未发现该关键词！")
            return
        }

        if (content.isNotEmpty()) {
            val replies = memes.first().replyContent.filter { content in it }
            if (replies.isNotEmpty()) {
                directlySend("查找到的回复: ${replies.take(8).joinToString()}")
            } else {
                directlySend("未发现该回复！")
            }
        } else {
            directlySend("查找到的关键词: ${memes.take(8).joinToString { "${it.name} (id: ${it.id})" }}")
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
     * 根据关键词查找有无对应Meme实例，未找到会默认回复相应提示
     */
    suspend fun OneBotMessageEvent.findMemeInstance(
        keyword: String,
        sendFeedback: Boolean = true,
        filter: SingleMeme.() -> Boolean = { true }
    ): SingleMeme? = findMemeInstances(keyword = keyword, filter = filter).firstOrNull() ?: run {
        if (sendFeedback) directlySend("未发现该关键词！")
        return null
    }

    /**
     * 根据关键词查找所有Meme实例
     */
    suspend fun OneBotMessageEvent.findMemeInstances(
        keyword: String,
        ambiguousMatch: Boolean = false,
        filter: SingleMeme.() -> Boolean = { true }
    ): List<SingleMeme> =
        memeConfig.memes.filter(filter).filter {
            try {
                UUID.fromString(keyword)
                return@filter keyword == it.id
            } catch (_: Exception) {
            }

            if (ambiguousMatch) {
                setOf(
                    it.name,
                    *it.alias.toTypedArray()
                ).map { word -> word.lowercase() }.any { word -> keyword.trim().lowercase() in word }
            } else {
                keyword.trim().lowercase() in setOf(
                    it.name,
                    *it.alias.toTypedArray()
                ).map { word -> word.lowercase() }
            }
        }
}
