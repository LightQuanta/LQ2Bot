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
import love.forte.simbot.logger.LoggerFactory
import love.forte.simbot.message.OfflineImage.Companion.toOfflineImage
import love.forte.simbot.message.messagesOf
import love.forte.simbot.message.toText
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import love.forte.simbot.resource.toResource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import tech.lq0.interceptor.ChinesePunctuationReplace
import tech.lq0.interceptor.RequireAdmin
import tech.lq0.interceptor.RequireBotAdmin
import tech.lq0.utils.*
import java.net.URI
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

@Component
class LiveNotify @Autowired constructor(app: Application) {
    val logger = LoggerFactory.getLogger("LIVE")

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val client = HttpClient()

            while (true) {
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
                    logger.error("批量获取直播间信息失败：$e")
                    delay(30.seconds)
                    continue
                }

                val roomInfo = responseData.data.map { it.value }

                // 检测并通知开播/下播
                for (info in roomInfo) {
                    with(info) {
                        val filteredName = if (uid.toString() in sensitiveLivers) "UID: $uid" else {
                            if (name.isSensitive()) {
                                logger.warn("检测到主播${name}(UID: ${uid})名称疑似含有敏感词，已替换为UID")
                                sensitiveLivers += uid.toString()
                                saveConfig("LiveNotify", "sensitiveLivers.json", Json.encodeToString(sensitiveLivers))
                                "UID: $uid"
                            } else name
                        }

                        if (liveStatus == 1 && liveTime > lastLiveTime.getOrDefault(uid.toString(), 0)) {
                            // 开播通知
                            logger.info("检测到${name}(UID: ${uid})开播")

                            lastLiveTime[uid.toString()] = liveTime
                            val groups = liveUIDBind[uid.toString()]!!
                            var succeedCount = 0

                            val filteredTitle = if (uid.toString() in sensitiveLivers) "" else {
                                if (title.isSensitive()) {
                                    logger.warn("检测到主播${name}(UID: ${uid})直播间标题疑似含有敏感词，已替换为UID")
                                    sensitiveLivers += uid.toString()
                                    saveConfig(
                                        "LiveNotify",
                                        "sensitiveLivers.json",
                                        Json.encodeToString(sensitiveLivers)
                                    )
                                    ""
                                } else title
                            }

                            val image = URI(cover).toURL().toResource().toOfflineImage()
                            for (group in groups) {
                                try {
                                    bot.groupRelation?.group(group.ID)?.send(
                                        messagesOf(
                                            "${filteredName}开播了！\n$filteredTitle\nhttps://live.bilibili.com/$roomId".toText(),
                                            image,
                                        )
                                    ) ?: throw Exception("获取群${group.ID}失败")
                                    succeedCount++
                                } catch (e: Exception) {
                                    logger.error("向群${group.ID}推送${name}(UID: ${uid})开播通知失败：$e")
                                }
                                delay(1.seconds)
                            }
                            logger.info("已向[${succeedCount}/${groups.size}]个群推送${name}(UID: ${uid})的开播通知")
                        } else if (liveStatus != 1 && lastLiveTime.getOrDefault(uid.toString(), 0) > 1) {
                            // 下播通知
                            logger.info("检测到${name}(UID: ${uid})下播")

                            lastLiveTime -= uid.toString()
                            val groups = liveUIDBind[uid.toString()]!!
                            var succeedCount = 0

                            for (group in groups) {
                                try {
                                    bot.groupRelation?.group(group.ID)?.send("${filteredName}下播了！")
                                        ?: throw Exception("获取群${group.ID}失败")
                                    succeedCount++
                                } catch (e: Exception) {
                                    logger.error("向群${group.ID}推送${name}(UID: ${uid})下播通知失败：$e")
                                }
                                delay(1.seconds)
                            }
                            logger.info("已向[${succeedCount}/${groups.size}]个群推送${name}(UID: ${uid})的下播通知")
                        }
                    }
                }

                saveConfig("LiveNotify", "lastLiveTime.json", Json.encodeToString(lastLiveTime))
                delay(30.seconds)
            }
        }
    }

    @Listener
    @RequireAdmin
    @ChinesePunctuationReplace
    @Filter("!{{operation,(un)?subscribe}} {{uids,\\d+([, ]+\\d+)*}}")
    suspend fun OneBotNormalGroupMessageEvent.subscribe(
        @FilterValue("operation") operation: String,
        @FilterValue("uids") uids: String,
    ) {
        val uidList = uids.split(Regex("[, ]+"))
        if (operation == "subscribe") {
            val subscribedCount = liveUIDBind.filter { groupId.toString() in it.value }.size
            val subscribeList = uidList.take((100 - subscribedCount).coerceAtLeast(0))
            if (subscribeList.isEmpty()) {
                directlySend("订阅的主播数量超出最大限制！")
                return
            }

            for (uid in subscribeList) {
                val subscribedGroups = liveUIDBind.getOrPut(uid) { mutableSetOf() }
                subscribedGroups += groupId.toString()
            }
            logger.info("群${groupId}(${content().name})订阅了${subscribeList.size}个主播，UID：${subscribeList.joinToString()}")
            directlySend("已订阅${subscribeList.size}个主播")
        } else {
            for (uid in uidList) {
                val bindGroups = liveUIDBind[uid]?.also { it -= groupId.toString() }
                if (bindGroups.isNullOrEmpty()) liveUIDBind -= uid
            }
            logger.info("群${groupId}(${content().name})取消订阅了${uidList.size}个主播，UID：${uidList.joinToString()}")
            directlySend("已取消订阅${uidList.size}个主播")
        }
        saveConfig("LiveNotify", "liveUIDBind.json", Json.encodeToString(liveUIDBind))
    }

    @Listener
    @ChinesePunctuationReplace
    @Filter("!showsubscribe")
    suspend fun OneBotGroupMessageEvent.showSubscribe() = showAnySubscribe(groupId.toString())

    @Listener
    @RequireBotAdmin
    @ChinesePunctuationReplace
    @Filter("!showsubscribe {{group,\\d+}}")
    suspend fun OneBotMessageEvent.showAnySubscribe(@FilterValue("group") group: String) {
        val subscribedUIDs = liveUIDBind.filter { group in it.value }.map { it.key }

        directlySend(
            if (subscribedUIDs.isEmpty()) {
                "该群还没有订阅主播！"
            } else {
                "该群订阅的主播UID：${subscribedUIDs.joinToString()}"
            }
        )
    }

    @Listener
    @RequireBotAdmin
    @ChinesePunctuationReplace
    @Filter("!showsubscribe uid:{{uid,\\d+}}")
    suspend fun OneBotMessageEvent.showUIDSubscribe(@FilterValue("uid") uid: String) {
        val subscribedGroups = liveUIDBind[uid] ?: emptySet()
        directlySend(
            if (subscribedGroups.isEmpty()) {
                "目前还没有群订阅该主播！"
            } else {
                "订阅该主播的${subscribedGroups.size}个群：${subscribedGroups.joinToString()}"
            }
        )
    }
}