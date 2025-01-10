package tech.lq0.plugins

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.message.OfflineImage.Companion.toOfflineImage
import love.forte.simbot.message.messagesOf
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.Listener
import love.forte.simbot.resource.toResource
import org.springframework.stereotype.Component
import tech.lq0.interceptor.FunctionSwitch
import tech.lq0.utils.chatLogger
import tech.lq0.utils.directlySend
import java.net.URL

@Component
class CatPic {
    val client = HttpClient()
    val pictureLinksCache = mutableListOf<String>()

    @Listener
    @FunctionSwitch("CatPic")
    @Filter("来点(猫(猫|图)|哈基米)")
    suspend fun OneBotMessageEvent.cat() = coroutineScope {
        if (pictureLinksCache.size == 0) {
            // 没有图片缓存时获取10张
            if (!updatePictureLinksCache()) {
                directlySend("获取猫图出错！")
                return@coroutineScope
            }
        } else if (pictureLinksCache.size <= 2) {
            // 图片缓存数量不足时尝试发起一次异步获取，不处理获取失败的情况
            launch { updatePictureLinksCache() }
        }

        directlySend(messagesOf(URL(pictureLinksCache.removeAt(0)).toResource().toOfflineImage()))
    }

    suspend fun updatePictureLinksCache(): Boolean {
        try {
            val resp = Json.parseToJsonElement(
                client.get("https://api.thecatapi.com/v1/images/search?limit=10").readBytes().decodeToString()
            )
            val count = resp.jsonArray.size
            pictureLinksCache.addAll(resp.jsonArray.map { it.jsonObject["url"]!!.jsonPrimitive.content })
            chatLogger.info("已更新 $count 张猫图缓存，累计缓存数量: ${pictureLinksCache.size}")
            return true
        } catch (e: Exception) {
            chatLogger.error("更新猫图缓存失败: $e")
            return false
        }
    }

}
