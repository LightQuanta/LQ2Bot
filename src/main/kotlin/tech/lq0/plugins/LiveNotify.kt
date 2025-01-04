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
import love.forte.simbot.common.id.StringID.Companion.ID
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
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
import java.util.concurrent.TimeUnit
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
    val uid: Long,
)

val json = Json {
    ignoreUnknownKeys = true
}

/**
 * 根据秒级时间戳计算时间差值字符串
 */
fun getTimeDiffStr(startTime: Long, endTime: Long): String {
    val diffInMillis = endTime - startTime
    val diffInMinutes = TimeUnit.SECONDS.toMinutes(diffInMillis)
    val diffInHours = TimeUnit.SECONDS.toHours(diffInMillis)
    val diffInDays = TimeUnit.SECONDS.toDays(diffInMillis)

    return when {
        diffInDays > 0 -> "${diffInDays}天${diffInHours}小时${diffInMinutes}分"
        diffInHours > 0 -> "${diffInHours}小时${diffInMinutes}分"
        diffInMinutes > 0 -> "${diffInMinutes}分"
        else -> "不到一分钟"
    }
}

/**
 * 单个群最多允许订阅的主播数量
 */
const val MAXIMUM_SUBSCRIBE_COUNT = 300

@Component
class LiveNotify @Autowired constructor(app: Application) {

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val client = HttpClient()

            while (true) {
                try {
                    // 暂不考虑多Bot支持，直接暴力轮询获取bot实例（）
                    val bot = try {
                        app.botManagers.firstBot()
                    } catch (e: Exception) {
                        delay(5.seconds)
                        continue
                    }

                    val subscribedUIDs = liveUIDBind.filter { it.value.isNotEmpty() }.map { it.key.toLong() }
                    if (subscribedUIDs.isEmpty()) {
                        delay(30.seconds)
                        continue
                    }

                    val responseData: BiliApiResponse<Map<String, RoomInfo>> = try {
                        json.decodeFromString(
                            client.post("https://api.live.bilibili.com/room/v1/Room/get_status_info_by_uids") {
                                contentType(ContentType.Application.Json)
                                setBody("""{"uids":[${subscribedUIDs.joinToString(",")}]}""")
                            }.readBytes().decodeToString()
                        )
                    } catch (e: Exception) {
                        liveLogger.error("批量获取直播间信息失败: $e")
                        delay(30.seconds)
                        continue
                    }
                    if (responseData.code != 0) {
                        liveLogger.error("批量获取直播间信息失败: ${responseData.msg}")
                        delay(30.seconds)
                        continue
                    }

                    val roomInfo = responseData.data.map { it.value }

                    var nameCacheChanged = false

                    // 检测并通知开播/下播
                    for (info in roomInfo) {
                        with(info) {
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
                                        nameCacheChanged = true
                                        UIDNameCache[uid.toString()] = name
                                    }
                                }
                            }

                            val groups = liveUIDBind[uid.toString()]!!.filter {
                                it !in botPermissionConfig.groupBlackList
                                        && it !in botPermissionConfig.groupDisabledList
                                        && "LiveNotify" !in (groupPluginConfig[it]?.disabled ?: setOf())
                            }

                            if (liveStatus == 1 && liveTime > lastLiveTime.getOrDefault(uid.toString(), 0)) {
                                // 开播通知
                                liveLogger.info("检测到 UID: $uid($name) 开播，时间戳: $liveTime")
                                lastLiveTime[uid.toString()] = liveTime

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

                                val succeedGroups = mutableListOf<String>()
                                val image = try {
                                    URL(cover).toResource().toOfflineImage()
                                } catch (e: Exception) {
                                    liveLogger.error("获取封面 $cover 失败: $e")
                                    null
                                }

                                for (group in groups) {
                                    val groupConfig = liveGroupConfig[group] ?: LiveNotifyGroupConfig()
                                    try {
                                        bot.groupRelation?.group(group.ID)?.send(
                                            buildMessages {
                                                add(buildString {
                                                    appendLine("${filteredName}开播了！")
                                                    if (groupConfig.showTitle) appendLine(filteredTitle)
                                                    if (groupConfig.showLink) appendLine("https://live.bilibili.com/$roomId")
                                                })
                                                if (groupConfig.showCover && image != null) add(image)
                                            }
                                        ) ?: throw Exception("获取群 ${group.ID} 失败")
                                        succeedGroups += group.ID.toString()
                                    } catch (e: Exception) {
                                        liveLogger.error("向群 ${group.ID} 推送 UID: $uid($name) 开播通知失败: $e")
                                    }
                                    delay(1.seconds)
                                }
                                liveLogger.info("已向[${succeedGroups.size}/${groups.size}]个群推送 UID: $uid($name) 的开播通知: ${succeedGroups.joinToString()}")
                            } else if (liveStatus != 1 && lastLiveTime.getOrDefault(uid.toString(), 0) > 1) {
                                // 下播通知

                                val succeedGroups = mutableListOf<String>()
                                val liveStartTime = lastLiveTime[uid.toString()]!!
                                val liveEndTime = System.currentTimeMillis() / 1000
                                lastLiveTime -= uid.toString()

                                liveLogger.info("检测到 UID: $uid($name) 下播，开播下播时间戳: $liveStartTime $liveEndTime")
                                for (group in groups) {
                                    val groupConfig = liveGroupConfig[group] ?: LiveNotifyGroupConfig()
                                    if (!groupConfig.notifyStopStream) continue

                                    try {
                                        bot.groupRelation?.group(group.ID)?.send(
                                            buildString {
                                                append("${filteredName}下播了！")
                                                if (groupConfig.showStreamTime) {
                                                    append(
                                                        "本次直播时长: ${getTimeDiffStr(liveStartTime, liveEndTime)}"
                                                    )
                                                }
                                            }
                                        ) ?: throw Exception("获取群 ${group.ID} 失败")
                                        succeedGroups += group.ID.toString()
                                    } catch (e: Exception) {
                                        liveLogger.error("向群 ${group.ID} 推送 UID: $uid($name) 下播通知失败: $e")
                                    }
                                    delay(1.seconds)
                                }

                                liveLogger.info("已向[${succeedGroups.size}/${groups.size}]个群推送 UID: $uid($name) 的下播通知: ${succeedGroups.joinToString()}")
                            }
                        }
                    }

                    if (nameCacheChanged) {
                        saveConfig("Cache", "UID2Name.json", Json.encodeToString(UIDNameCache))
                    }

                    saveConfig("LiveNotify", "lastLiveTime.json", Json.encodeToString(lastLiveTime), false)
                    delay(30.seconds)
                } catch (e: Exception) {
                    liveLogger.error("LiveNotify 监听器发生错误: $e")
                    delay(30.seconds)
                }
            }
        }
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
            if (subscribed.size >= MAXIMUM_SUBSCRIBE_COUNT) {
                directlySend("订阅的主播数量已达到允许的最大值！")
                return
            }

            val newSubscribeList =
                (uidList - subscribed).take((MAXIMUM_SUBSCRIBE_COUNT - subscribed.size).coerceAtLeast(0))
            if (newSubscribeList.isEmpty()) {
                directlySend("该群已经订阅上述全部主播！")
                return
            }

            for (uid in newSubscribeList) {
                val subscribedGroups = liveUIDBind.getOrPut(uid) { mutableSetOf() }
                subscribedGroups += groupId.toString()
            }
            liveLogger.info(
                "群 $groupId(${content().name}) 订阅了${newSubscribeList.size}个主播: ${
                    newSubscribeList.joinToString { getUIDNameString(it) }
                }"
            )
            directlySend(
                "已订阅以下${newSubscribeList.size}个主播: \n${newSubscribeList.joinToString { getUIDNameString(it) }}"
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
    @Filter("!liveconfig {{config,\\w+}} {{operation,.+}}")
    suspend fun OneBotNormalGroupMessageEvent.configLive(
        @FilterValue("config") config: String,
        @FilterValue("operation") operation: String,
    ) {
        if (config.lowercase().trim() !in setOf(
                "notifystopstream",
                "showstreamtime",
                "showtitle",
                "showcover",
                "showlink",
                "下播通知",
                "显示直播时长",
                "显示直播间标题",
                "显示直播间封面",
                "显示直播间链接",
            )
        ) {
            directlySend("无法识别配置项，请输入下列选项其中之一: \n下播通知 显示直播时长 显示直播间标题 显示直播间封面 显示直播间链接")
            return
        }

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
            "notifystopstream", "下播通知" -> liveConfig.notifyStopStream = enable
            "showstreamtime", "显示直播时长" -> liveConfig.showStreamTime = enable
            "showtitle", "显示直播间标题" -> liveConfig.showTitle = enable
            "showcover", "显示直播间封面" -> liveConfig.showCover = enable
            "showlink", "显示直播间链接" -> liveConfig.showLink = enable
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