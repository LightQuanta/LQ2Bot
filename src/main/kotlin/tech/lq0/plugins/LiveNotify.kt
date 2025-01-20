package tech.lq0.plugins

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import love.forte.simbot.application.Application
import love.forte.simbot.bot.Bot
import love.forte.simbot.common.id.StringID.Companion.ID
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.message.Messages
import love.forte.simbot.message.OfflineImage.Companion.toOfflineImage
import love.forte.simbot.message.buildMessages
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import love.forte.simbot.resource.toResource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import tech.lq0.interceptor.ChinesePunctuationReplace
import tech.lq0.interceptor.FunctionSwitch
import tech.lq0.interceptor.RequireAdmin
import tech.lq0.interceptor.RequireBotAdmin
import tech.lq0.utils.*
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


@Serializable
data class BiliApiResponse<T>(
    val code: Int,
    val msg: String,
    val message: String,
    val data: T
)

@Serializable
data class RoomInfo(
    val title: String,
    @SerialName("cover_from_user") val cover: String,
    @SerialName("live_time") val liveTime: Long,
    @SerialName("live_status") val liveStatus: Int,
    @SerialName("uname") val name: String,
    @SerialName("room_id") val roomId: Long,
    @SerialName("area_v2_name") val areaName: String,
    @SerialName("area_v2_parent_name") val parentAreaName: String,
    val uid: Long,
)

val json = Json {
    ignoreUnknownKeys = true
}

/**
 * 根据秒级时间戳计算时间差值字符串
 */
fun getTimeDiffStr(startTime: Long, endTime: Long): String {
    val diff = endTime - startTime
    val diffInDays = diff / (60 * 60 * 24)
    val diffInHours = diff % (60 * 60 * 24) / (60 * 60)
    val diffInMinutes = diff % (60 * 60) / 60

    return when {
        diffInDays > 0 -> "${diffInDays}天${diffInHours}小时${diffInMinutes}分钟"
        diffInHours > 0 -> "${diffInHours}小时${diffInMinutes}分钟"
        diffInMinutes > 0 -> "${diffInMinutes}分钟"
        else -> "不到一分钟"
    }
}

/**
 * 单个群最多允许订阅的主播数量
 */
const val GROUP_MAXIMUM_SUBSCRIBE_COUNT = 300

/**
 * 全局最多允许订阅的主播数量
 */
const val GLOBAL_MAXIMUM_SUBSCRIBE_COUNT = 5000

/**
 * 直播状态轮询间隔（秒）
 */
const val POLLING_DELAY = 30

@Component
class LiveNotify @Autowired constructor(app: Application) {

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val client = HttpClient()

            while (true) {
                markQueryStart()

                try {
                    // 暂不考虑多Bot支持，直接暴力轮询获取bot实例（）
                    val bot = try {
                        app.botManagers.firstBot()
                    } catch (e: Exception) {
                        delayAfterQuery(5.seconds)
                        continue
                    }

                    val subscribedUIDs = liveUIDBind.filter { it.value.isNotEmpty() }.map { it.key.toLong() }
                    if (subscribedUIDs.isEmpty()) {
                        delayAfterQuery(POLLING_DELAY.seconds)
                        continue
                    }

                    val responseData: BiliApiResponse<Map<String, RoomInfo>> = try {
                        json.decodeFromString(
                            client.post("https://api.live.bilibili.com/room/v1/Room/get_status_info_by_uids") {
                                contentType(ContentType.Application.Json)
                                setBody("""{"uids":[${subscribedUIDs.joinToString(",")}]}""")
                            }.bodyAsText()
                        )
                    } catch (e: Exception) {
                        liveLogger.error("批量获取直播间信息失败: $e")
                        delayAfterQuery(POLLING_DELAY.seconds)
                        continue
                    }
                    if (responseData.code != 0) {
                        liveLogger.error("批量获取直播间信息失败: ${responseData.msg}")
                        delayAfterQuery(POLLING_DELAY.seconds)
                        continue
                    }

                    // 获取所有主播的当前直播间信息
                    val roomInfo = responseData.data.map { it.value }

                    // 检测并通知直播间状态改变信息
                    // 不要使用forEach，否则delay会失效
                    for (info in roomInfo) {
                        info.informStatusChange(bot)

                        // 记得更新开播状态更新开播状态更新开播状态！！！
                        liveStateCache[info.uid.toString()] = info
                    }

                    saveConfig("Cache", "UID2Name.json", Json.encodeToString(UIDNameCache), false)
                    saveConfig("LiveNotify", "liveStateCache.json", Json.encodeToString(liveStateCache), false)

                    delayAfterQuery(POLLING_DELAY.seconds)
                } catch (e: Exception) {
                    liveLogger.error("LiveNotify 监听器发生错误: $e")
                    delayAfterQuery(POLLING_DELAY.seconds)
                }
            }
        }
    }

    /**
     * 根据获取到的单个主播的直播间信息，检测并通知对应的群聊
     */
    suspend fun RoomInfo.informStatusChange(bot: Bot) {
        // 获取用户名，检测敏感词，缓存 UID -> 名称 对应关系
        val isNameSensitive = name.isSensitive()
        val filteredName = if (uid.toString() in sensitiveLivers) "UID: $uid" else {
            if (isNameSensitive) {
                liveLogger.warn("检测到主播 UID: $uid($name) 名称疑似含有敏感词，已替换为UID")
                sensitiveLivers += uid.toString()
                saveConfig(
                    "LiveNotify",
                    "sensitiveLivers.json",
                    Json.encodeToString(sensitiveLivers)
                )
                "UID: $uid"
            } else name.also {
                if (UIDNameCache.getOrDefault(uid.toString(), "") != name) {
                    UIDNameCache[uid.toString()] = name
                }
            }
        }

        // 获取直播间状态缓存，重置liveTime和liveStatus以便在刚订阅时立刻发送开播通知
        val lastTimeRoomStatus = liveStateCache.getOrPut(uid.toString()) { copy(liveTime = 0, liveStatus = 0) }

        // 获取经过敏感词检测后的标题
        val filteredTitle = if (uid.toString() in sensitiveLivers) "" else {
            if (title.isSensitive()) {
                liveLogger.warn("检测到主播 UID: $uid($name) 直播间标题($title)疑似含有敏感词，已替换为UID")
                sensitiveLivers += uid.toString()
                saveConfig(
                    "LiveNotify",
                    "sensitiveLivers.json",
                    Json.encodeToString(sensitiveLivers)
                )
                ""
            } else title
        }

        // 标题更改通知
        if (title != lastTimeRoomStatus.title) {
            liveLogger.info("检测到 UID: $uid($name) 直播间标题由 ${lastTimeRoomStatus.title} 更新为 $title")

            // 不推送敏感主播的直播间标题更改
            if (uid.toString() !in sensitiveLivers) {
                if (liveStatus == 1 && lastTimeRoomStatus.liveStatus == liveStatus) {
                    // 开播时标题更改通知
                    informSubscribedGroups(uid.toString(), bot) {
                        if (notifyTitleChangeWhileStreaming && filteredTitle.isNotEmpty()) {
                            buildMessages { add("$filteredName 更改了直播间标题: ${lastTimeRoomStatus.title} -> $filteredTitle") }
                        } else null
                    }
                } else {
                    // 下播时标题更改通知
                    informSubscribedGroups(uid.toString(), bot) {
                        if (notifyTitleChangeWhileNotStreaming && filteredTitle.isNotEmpty()) {
                            buildMessages { add("$filteredName 更改了直播间标题: ${lastTimeRoomStatus.title} -> $filteredTitle") }
                        } else null
                    }
                }
            }
        }

        if (liveStatus == 1 && liveTime > lastTimeRoomStatus.liveTime) {
            // 开播通知
            liveLogger.info("检测到 UID: $uid($name) 开播，本次获取延迟: ${System.currentTimeMillis() / 1000 - liveTime}秒，开播时间戳: $liveTime")

            //  尝试获取封面（为什么有的直播间封面为空）
            val image = try {
                URL(cover).toResource().toOfflineImage()
            } catch (e: Exception) {
                liveLogger.error("获取封面(URL: $cover)失败: $e")
                null
            }

            // 通知对应群聊
            informSubscribedGroups(uid.toString(), bot) {
                buildMessages {
                    add(buildString {
                        append(filteredName)
                        if (showLiveArea) append(" 在$parentAreaName-${areaName}分区")

                        // 开播时间更新但开播状态不变，即在bot轮询期间内重新开播
                        if (lastTimeRoomStatus.liveStatus == 1) append("重新")

                        appendLine("开播了！")

                        if (showTitle) appendLine(filteredTitle)
                        if (showLink) appendLine("https://live.bilibili.com/$roomId")
                    })
                    if (showCover && image != null) add(image)
                }
            }
        } else if (liveStatus != 1 && lastTimeRoomStatus.liveTime > 1) {
            // 下播通知
            val liveStartTime = lastTimeRoomStatus.liveTime
            val liveEndTime = System.currentTimeMillis() / 1000

            liveLogger.info("检测到 UID: $uid($name) 下播，开播下播时间戳: $liveStartTime $liveEndTime")

            informSubscribedGroups(uid.toString(), bot) {
                if (notifyStopStream) {
                    buildMessages {
                        add(buildString {
                            append("${filteredName}下播了！")
                            if (showStreamTime) append("本次直播时长: ${getTimeDiffStr(liveStartTime, liveEndTime)}")
                        })
                    }
                } else null
            }
        }

    }


    /**
     * 向订阅了指定主播的所有群推送消息
     * @param uid 主播 UID
     * @param bot 机器人实例（用于推送通知）
     * @param messagesBuilder 消息构造器，返回要推送的消息，若为空则不推送
     */
    suspend fun informSubscribedGroups(uid: String, bot: Bot, messagesBuilder: LiveNotifyGroupConfig.() -> Messages?) {
        // 获取所有订阅了该主播（并且直播推送功能正常启用）的群
        val groups = liveUIDBind[uid]!!.filter {
            it !in botPermissionConfig.groupBlackList
                    && it !in botPermissionConfig.groupDisabledList
                    && "LiveNotify" !in (groupPluginConfig[it]?.disabled ?: setOf())
        }

        val succeedGroups = mutableListOf<String>()
        for (group in groups) {
            val groupConfig = liveGroupConfig[group] ?: LiveNotifyGroupConfig()
            val messages = messagesBuilder(groupConfig)

            // 消息为空时，不进行通知
            if (messages == null) {
                succeedGroups += group.ID.toString()
                continue
            }

            try {
                bot.groupRelation?.group(group.ID)?.send(messages) ?: throw Exception("获取群 ${group.ID} 失败")
                succeedGroups += group.ID.toString()
            } catch (e: Exception) {
                liveLogger.error("向群 ${group.ID} 推送 ${getUIDNameString(uid)} 的直播通知失败: $e")
            }
            delay(0.5.seconds)
        }
        liveLogger.info("已向[${succeedGroups.size}/${groups.size}]个群推送 ${getUIDNameString(uid)} 的直播通知: ${succeedGroups.joinToString()}")
    }

    var queryStartTime: Long = 0

    /**
     * 标记轮询开始时间，以便计算准确的下次轮询开始时间
     */
    fun markQueryStart() {
        queryStartTime = System.currentTimeMillis()
    }

    /**
     * 在开始轮询后准确延迟指定时长
     */
    suspend fun delayAfterQuery(duration: kotlin.time.Duration) {
        val delay = (queryStartTime + duration.inWholeMilliseconds - System.currentTimeMillis()).milliseconds
        delay(delay)
    }

    @Listener
    @RequireAdmin
    @FunctionSwitch("LiveNotify")
    @ChinesePunctuationReplace
    @Filter("!{{operation,(un)?subscribe}} \\D*{{uids,.+}}")
    suspend fun OneBotNormalGroupMessageEvent.subscribe(
        @FilterValue("operation") operation: String,
        @FilterValue("uids") uids: String,
    ) {
        // 针对 114514(主播名字1919810) 这类格式的输入，剔除括号中的数字匹配，只获取114514
        val uidList = uids.split(Regex("\\D*(\\(.+?\\)|\\[.+?])\\D*|\\D+", RegexOption.MULTILINE))
            .filter { it.isNotEmpty() }
            .distinct()
        if (uidList.isEmpty()) {
            directlySend("无法识别要操作的主播UID，请正确输入！")
            return
        }

        if (operation == "subscribe") {
            val subscribed = liveUIDBind.filter { groupId.toString() in it.value }.keys

            // 单个群最大订阅数量限制
            if (subscribed.size >= GROUP_MAXIMUM_SUBSCRIBE_COUNT) {
                directlySend("本群订阅的主播数量已达到最大值！")
                return
            }

            // 全局订阅数量限制
            if (liveUIDBind.size >= GLOBAL_MAXIMUM_SUBSCRIBE_COUNT) {
                directlySend("全局订阅的主播数量已达到最大值！")
                return
            }

            val limitedSubscribeList = (uidList - subscribed)
                .take((GROUP_MAXIMUM_SUBSCRIBE_COUNT - subscribed.size).coerceAtLeast(0))
                .take((GLOBAL_MAXIMUM_SUBSCRIBE_COUNT - liveUIDBind.size).coerceAtLeast(0))

            for (uid in limitedSubscribeList) {
                val subscribedGroups = liveUIDBind.getOrPut(uid) { mutableSetOf() }
                subscribedGroups += groupId.toString()
            }
            liveLogger.info(
                "群 $groupId(${content().name}) 订阅了${limitedSubscribeList.size}个主播: ${
                    limitedSubscribeList.joinToString { getUIDNameString(it) }
                }"
            )
            directlySend(
                "已订阅以下${limitedSubscribeList.size}个主播: \n${
                    limitedSubscribeList.joinToString { getUIDNameString(it) }
                }"
            )
        } else {
            val subscribed = liveUIDBind.filter { groupId.toString() in it.value }.keys
            val uidToRemove = uidList.intersect(subscribed)

            if (uidToRemove.isEmpty()) {
                directlySend("该群没有订阅上述任何主播！")
                return
            }

            for (uid in uidToRemove) {
                val bindGroups = liveUIDBind[uid]!!.also { it -= groupId.toString() }
                if (bindGroups.isEmpty()) liveUIDBind -= uid
            }
            liveLogger.info(
                "群 $groupId(${content().name}) 取消订阅了${uidToRemove.size}个主播: ${
                    uidToRemove.joinToString { getUIDNameString(it) }
                }"
            )
            directlySend(
                "已取消订阅以下${uidToRemove.size}个主播: \n${uidToRemove.joinToString { getUIDNameString(it) }}"
            )
        }
        saveConfig("LiveNotify", "liveUIDBind.json", Json.encodeToString(liveUIDBind))
    }

    @Listener
    @RequireAdmin
    @FunctionSwitch("LiveNotify")
    @ChinesePunctuationReplace
    @Filter("!liveconfig {{config,\\S+}} {{operation,\\S+}}")
    suspend fun OneBotNormalGroupMessageEvent.configLive(
        @FilterValue("config") config: String,
        @FilterValue("operation") operation: String,
    ) {
        val enable = when (operation.trim().lowercase()) {
            "on", "enable", "true", "ture", "开", "启用", "开启", "1", "ok" -> true
            "off", "disable", "false", "flase", "关", "禁用", "关闭", "0", "cancel" -> false
            else -> {
                directlySend("无法识别操作，请输入 启用/禁用、开/关、on/off、true/false 等可以识别的操作！")
                return
            }
        }

        val liveConfig = liveGroupConfig.getOrPut(groupId.toString()) { LiveNotifyGroupConfig() }
        when (config.lowercase().trim()) {
            "下播通知" -> liveConfig.notifyStopStream = enable
            "显示直播时长" -> liveConfig.showStreamTime = enable
            "显示直播间标题" -> liveConfig.showTitle = enable
            "显示直播间封面" -> liveConfig.showCover = enable
            "显示直播间链接" -> liveConfig.showLink = enable
            "显示直播分区" -> liveConfig.showLiveArea = enable
            "直播时通知直播间标题更改" -> liveConfig.notifyTitleChangeWhileStreaming = enable
            "非直播时通知直播间标题更改" -> liveConfig.notifyTitleChangeWhileNotStreaming = enable
            else -> {
                directlySend("无法识别配置项，请输入下列选项其中之一: \n下播通知 显示直播时长 显示直播间标题 显示直播间封面 显示直播间链接 显示直播分区 直播时通知直播间标题更改 非直播时通知直播间标题更改")
                return
            }
        }

        liveLogger.info("群 $groupId(${content().name}) 的直播通知配置项 $config 已被 $authorId(${this.author().name}) 设置为 $enable")
        saveConfig("LiveNotify", "liveGroupConfig.json", Json.encodeToString(liveGroupConfig))
        directlySend("设置成功！")
    }

    @Listener
    @FunctionSwitch("LiveNotify")
    @ChinesePunctuationReplace
    @Filter("!showliveconfig")
    suspend fun OneBotGroupMessageEvent.showConfig() {
        val liveConfig = liveGroupConfig[groupId.toString()] ?: LiveNotifyGroupConfig()
        directlySend(
            """
                直播通知配置
                
                下播通知: ${if (liveConfig.notifyStopStream) "开" else "关"}
                显示直播时长: ${if (liveConfig.showStreamTime) "开" else "关"}
                显示直播间标题: ${if (liveConfig.showTitle) "开" else "关"}
                显示直播间封面: ${if (liveConfig.showCover) "开" else "关"}
                显示直播间链接: ${if (liveConfig.showLink) "开" else "关"}
                显示直播分区: ${if (liveConfig.showLiveArea) "开" else "关"}
                直播时通知直播间标题更改: ${if (liveConfig.notifyTitleChangeWhileStreaming) "开" else "关"}
                非直播时通知直播间标题更改: ${if (liveConfig.notifyTitleChangeWhileNotStreaming) "开" else "关"}
            """.trimIndent()
        )
    }

    @Listener
    @FunctionSwitch("LiveNotify")
    @ChinesePunctuationReplace
    @Filter("!showsubscribe")
    suspend fun OneBotGroupMessageEvent.showSubscribe() = showAnySubscribe(groupId.toString())

    @Listener
    @RequireBotAdmin
    @ChinesePunctuationReplace
    @Filter("!showsubscribe {{group,\\d+}}")
    suspend fun OneBotMessageEvent.showAnySubscribe(@FilterValue("group") group: String) {
        val subscribedUIDs = liveUIDBind.filter { group in it.value }.map { it.key }
        val nameOrUIDs = subscribedUIDs.map(::getUIDNameString)

        directlySend(
            if (subscribedUIDs.isEmpty()) {
                "该群还没有订阅主播！"
            } else {
                "该群订阅的${subscribedUIDs.size}个主播UID: ${nameOrUIDs.joinToString()}"
            }
        )
    }

    @Listener
    @RequireBotAdmin
    @ChinesePunctuationReplace
    @Filter("!showsubscribe uid: *{{uid,\\d+}}")
    suspend fun OneBotMessageEvent.showUIDSubscribe(@FilterValue("uid") uid: String) {
        val subscribedGroups = liveUIDBind[uid] ?: emptySet()
        val name = getUIDNameString(uid)

        directlySend(
            if (subscribedGroups.isEmpty()) {
                "目前还没有群订阅该主播！"
            } else {
                "订阅主播 $name 的${subscribedGroups.size}个群: ${subscribedGroups.joinToString()}"
            }
        )
    }

    @Listener
    @RequireAdmin
    @FunctionSwitch("LiveNotify")
    @ChinesePunctuationReplace
    @Filter("!clearsubscribe")
    suspend fun OneBotGroupMessageEvent.clearSubscribe() = clearAnySubscribe("", groupId.toString())

    @Listener
    @RequireBotAdmin
    @ChinesePunctuationReplace
    @Filter("!clearsubscribe {{operation,(uid:?)?}} *{{num,\\d+}}")
    suspend fun OneBotMessageEvent.clearAnySubscribe(
        @FilterValue("operation") operation: String,
        @FilterValue("num") num: String,
    ) {
        if (operation.take(3) == "uid") {
            // 清空订阅该UID的所有群，num为UID
            val groups = liveUIDBind[num]
            if (groups == null) {
                directlySend("该主播 ${getUIDNameString(num)} 没有任何群订阅！")
                return
            }
            liveUIDBind -= num

            saveConfig("LiveNotify", "liveUIDBind.json", Json.encodeToString(liveUIDBind))
            liveLogger.info("清空订阅了 ${getUIDNameString(num)} 的${groups.size}个群: ${groups.joinToString()}")
            directlySend("已清空订阅 ${getUIDNameString(num)} 的${groups.size}个群: ${groups.joinToString()}")
        } else {
            // 清空该群订阅的所有主播，num为群号
            if (liveUIDBind.any { num in it.value }) {
                val removed = liveUIDBind.filter { num in it.value }.map {
                    it.value -= num
                    it.key
                }.onEach { if (liveUIDBind[it]!!.isEmpty()) liveUIDBind -= it }

                saveConfig("LiveNotify", "liveUIDBind.json", Json.encodeToString(liveUIDBind))
                liveLogger.info("清空群 $num 订阅的${removed.size}个主播: ${removed.joinToString { getUIDNameString(it) }}")
                directlySend("已清空群 $num 订阅的${removed.size}个主播: ${removed.joinToString { getUIDNameString(it) }}")
            } else {
                directlySend("该群没有订阅任何主播！")
                return
            }
        }

    }
}