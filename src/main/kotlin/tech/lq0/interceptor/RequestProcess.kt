package tech.lq0.interceptor

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import love.forte.simbot.component.onebot.v11.core.event.notice.OneBotGroupMemberDecreaseEvent
import love.forte.simbot.component.onebot.v11.core.event.request.OneBotFriendRequestEvent
import love.forte.simbot.component.onebot.v11.core.event.request.OneBotGroupRequestEvent
import love.forte.simbot.event.RequestEvent
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import tech.lq0.utils.botPermissionConfig
import tech.lq0.utils.liveUIDBind
import tech.lq0.utils.saveConfig

@Component
class RequestProcess {

    /**
     * 自动接受非黑名单用户的好友请求
     */
    @Listener
    suspend fun OneBotFriendRequestEvent.add() {
        // 拒绝黑名单用户的请求
        if (requesterId.toString() in botPermissionConfig.memberBlackList) {
            reject()
        } else {
            accept()
        }
    }

    /**
     * 自动接受非黑名单群的邀请请求
     */
    @Listener
    suspend fun OneBotGroupRequestEvent.add() {
        // 只处理邀请加群请求
        if (type == RequestEvent.Type.PASSIVE) {
            // 禁止黑名单用户进行任何操作
            if (requesterId.toString() in botPermissionConfig.memberBlackList) return

            // 拒绝加入黑名单群
            if (content().id.toString() in botPermissionConfig.groupBlackList) {
                reject()
            } else {
                accept()
            }
        }
    }

    @Listener
    suspend fun OneBotGroupMemberDecreaseEvent.removeConfig() {
        if (sourceEvent.subType != "kick_me") return

        // bot被踢出群时，清空该群的相关配置文件
        val group = groupId.toString()

        // 清除开播通知订阅信息
        if (liveUIDBind.any { group in it.value }) {
            liveUIDBind.forEach { it.value -= group }
            saveConfig("LiveNotify", "liveUIDBind.json", Json.encodeToString(liveUIDBind))
        }
    }
}