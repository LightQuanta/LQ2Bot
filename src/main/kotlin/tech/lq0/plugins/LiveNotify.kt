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
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.logger.LoggerFactory
import love.forte.simbot.message.messagesOf
import love.forte.simbot.message.toText
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import tech.lq0.interceptor.ChinesePunctuationReplace
import tech.lq0.interceptor.RequireAdmin
import tech.lq0.utils.directlySend
import tech.lq0.utils.lastLiveTime
import tech.lq0.utils.liveUIDBind
import tech.lq0.utils.saveConfig
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
                        if (liveStatus == 1 && liveTime > lastLiveTime.getOrDefault(uid.toString(), 0)) {
                            // 开播通知
                            logger.info("检测到${name}(UID: ${uid})开播")

                            lastLiveTime[uid.toString()] = liveTime
                            val groups = liveUIDBind[uid.toString()]!!
                            var succeedCount = 0

                            for (group in groups) {
                                try {
                                    bot.groupRelation?.group(group.ID)?.send(
                                        messagesOf(
                                            "${name}开播了！\nhttps://live.bilibili.com/$roomId".toText(),
                                            // TODO URL图片发送
                                            // RemoteUrlAwareImage()
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
                                    bot.groupRelation?.group(group.ID)?.send("$${name}下播了！")
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
        for (uid in uidList) {
            if (operation == "subscribe") {
                val subscribedGroups = liveUIDBind.getOrPut(uid) { mutableSetOf() }
                subscribedGroups += groupId.toString()
            } else {
                val bindGroups = liveUIDBind[uid]?.also { it -= groupId.toString() }
                if (bindGroups.isNullOrEmpty()) liveUIDBind -= uid
            }
        }
        directlySend("已${if (operation == "unsubscribe") "取消" else ""}订阅${uidList.size}个主播")
        saveConfig("LiveNotify", "liveUIDBind.json", Json.encodeToString(liveUIDBind))
    }

    @Listener
    @ChinesePunctuationReplace
    @Filter("!showsubscribe")
    suspend fun OneBotNormalGroupMessageEvent.showSubscribe() {
        val subscribedUIDs = liveUIDBind.filter { groupId.toString() in it.value }.map { it.key }

        directlySend(
            if (subscribedUIDs.isEmpty()) {
                "本群还没有订阅主播！"
            } else {
                "本群订阅的主播UID：${subscribedUIDs.joinToString()}"
            }
        )
    }
}