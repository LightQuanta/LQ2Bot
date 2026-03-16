package tech.lq0.plugins

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.message.Messages
import love.forte.simbot.message.OfflineImage.Companion.toOfflineImage
import love.forte.simbot.message.messagesOf
import love.forte.simbot.message.toText
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import love.forte.simbot.resource.toResource
import org.springframework.stereotype.Component
import tech.lq0.interceptor.ChinesePunctuationReplace
import tech.lq0.interceptor.FunctionSwitch
import tech.lq0.interceptor.RequireAdmin
import tech.lq0.utils.*
import java.net.URL

private val client = HttpClient()

private val rateLimit = mutableMapOf<String, Long>()
private val randomUser
    get() = runBlocking {
        if (vtuberCache.get().uidList.isEmpty()) {
            updateVtuberList()
        }

        // 缓存24小时
        if (System.currentTimeMillis() - vtuberCache.get().lastUpdateTime > 1000 * 60 * 60 * 24) {
            launch {
                try {
                    updateVtuberList()
                } catch (e: Exception) {
                    chatLogger.error("获取管人列表失败: $e")
                }
            }
        }
        if (vtuberCache.get().uidList.isEmpty()) null else vtuberCache.get().uidList.random()
    }

private val userIntroCache = mutableMapOf<Long, Pair<Long, Messages>>()

private suspend fun getUserIntroByRoomID(roomID: Long): Messages? {
    try {
        // 直播间信息
        var response = Json.parseToJsonElement(
            client.get("https://api.live.bilibili.com/room/v1/Room/get_info?room_id=$roomID").bodyAsText()
        )
        if (response.jsonObject["code"]!!.jsonPrimitive.int != 0) throw Exception(Json.encodeToString(response))

        var data = response.jsonObject["data"]!!
        val uid = data.jsonObject["uid"]!!.jsonPrimitive.long
        val title = data.jsonObject["title"]!!.jsonPrimitive.content
        val isLiving = data.jsonObject["live_status"]!!.jsonPrimitive.int == 1

        // 用户信息
        response = Json.parseToJsonElement(
            client.get("https://api.bilibili.com/x/web-interface/card?mid=$uid") {
                userAgent("114514")
            }.bodyAsText()
        )
        if (response.jsonObject["code"]!!.jsonPrimitive.int != 0) throw Exception(Json.encodeToString(response))

        data = response.jsonObject["data"]!!.jsonObject["card"]!!
        val name = data.jsonObject["name"]!!.jsonPrimitive.content
        val face = data.jsonObject["face"]!!.jsonPrimitive.content
        val sign = data.jsonObject["sign"]!!.jsonPrimitive.content
        val fans = data.jsonObject["fans"]!!.jsonPrimitive.int

        if (UIDNameCache.get()[uid.toString()] != name && !name.isSensitive()) {
            UIDNameCache.get()[uid.toString()] = name
            UIDNameCache.save()
        }

        return generateUserIntro(name, uid.toString(), roomID.toString(), title, sign, face, fans, isLiving)
    } catch (e: Exception) {
        chatLogger.error("获取直播间信息(roomID: $roomID)失败: $e")
        return null
    }
}


private suspend fun getUserIntroByUID(uid: Long): Messages? {
    userIntroCache[uid]?.let { (time, messages) ->
        if (System.currentTimeMillis() - time < 5 * 60 * 1000) return messages
    }

    try {
        // UID获取直播间
        // 用户信息
        val response = Json.parseToJsonElement(
            client.get("https://api.live.bilibili.com/live_user/v1/Master/info?uid=$uid").bodyAsText()
        )
        if (response.jsonObject["code"]!!.jsonPrimitive.int != 0) throw Exception(Json.encodeToString(response))

        val roomID = response.jsonObject["data"]!!.jsonObject["room_id"]!!.jsonPrimitive.long

        val messages = getUserIntroByRoomID(roomID)?.also {
            userIntroCache[uid] = System.currentTimeMillis() to it
        }

        return messages
    } catch (e: Exception) {
        chatLogger.error("获取用户信息(uid: $uid)失败: $e")
        return null
    }
}

private fun generateUserIntro(
    name: String,
    uid: String,
    liveRoom: String,
    title: String,
    sign: String,
    face: String,
    fans: Int,
    isLiving: Boolean,
): Messages = messagesOf(
    URL("$face@150h").toResource().toOfflineImage(),
    if (isLiving) {
        """
                |${if (title.isSensitive()) "" else title}
                |https://live.bilibili.com/$liveRoom
                
                |${if (name.isSensitive()) "" else "名称: $name"}
                |${if (sign.isSensitive()) "" else "签名: $sign"}
                |粉丝数: $fans
                |主页: https://space.bilibili.com/$uid
            """.trimMargin()
    } else {
        """
                |${if (name.isSensitive()) "" else "名称: $name"}
                |${if (sign.isSensitive()) "" else "签名: $sign"}
                |粉丝数: $fans
                |主页: https://space.bilibili.com/$uid
                |直播间: https://live.bilibili.com/$liveRoom
            """.trimMargin()
    }.toText()
)

// 主播列表更新

private var updatingVtuberList = false
private suspend fun updateVtuberList() {

    if (updatingVtuberList) return
    updatingVtuberList = true

    val response = Json.parseToJsonElement(
        try {
            client.get("https://cfapi.vtbs.moe/v1/vtbs").bodyAsText()
        } catch (e: Exception) {
            chatLogger.error("获取管人列表失败: $e")
            updatingVtuberList = false
            return
        }
    )

    vtuberCache.get().lastUpdateTime = System.currentTimeMillis()
    vtuberCache.get().uidList = response.jsonArray.map { it.jsonObject["mid"]!!.jsonPrimitive.long }.toMutableSet()
    vtuberCache.save()

    updatingVtuberList = false
}

@Listener
@FunctionSwitch("DDTool")
@Filter("(今天|现在)(看|D|d)谁")
suspend fun OneBotMessageEvent.dd() {
    // 5分钟调用冷却
    if ((rateLimit[authorId.toString()] ?: 0) > System.currentTimeMillis()) {
        directlySend("DD过于频繁，请稍后再试")
        return
    }
    rateLimit[authorId.toString()] = System.currentTimeMillis() + 1000 * 60 * 5

    // 只响应不匿名群消息
    if (this is OneBotGroupMessageEvent && this !is OneBotNormalGroupMessageEvent) return

    // 随机抽取任意管人或者抽取指定单推管人
    val uid = if (this is OneBotNormalGroupMessageEvent && groupId.toString() in ddToolBind) {
        ddToolBind[groupId.toString()]
    } else randomUser

    uid?.let {
        getUserIntroByUID(uid)?.let { directlySend(it) } ?: directlySend("获取管人信息失败")
    } ?: directlySend("获取管人信息失败")
}


@Component
class DDTool {

    @Listener
    @FunctionSwitch("DDTool")
    @Filter("(今天|现在)(看|D|d)谁")
    suspend fun OneBotMessageEvent.ddCommand() {
        dd()
    }

    @Listener
    @FunctionSwitch("DDTool")
    @RequireAdmin
    @ChinesePunctuationReplace
    @Filter("!setddtool {{operation,.+}}")
    suspend fun OneBotNormalGroupMessageEvent.singlePush(@FilterValue("operation") operation: String) {
        if (operation == "reset") {
            ddToolBind -= groupId.toString()
            ddToolBind.save()
            directlySend("已删除指定抽取用户！")
            return
        }
        val uid = operation.toLongOrNull()
        if (uid == null) {
            directlySend("输入的UID格式错误！")
            return
        }

        ddToolBind[groupId.toString()] = uid
        ddToolBind.save()
        directlySend("已设置指定抽取用户: ${getUIDNameString(uid.toString())}")
    }


}
