package tech.lq0.interceptor

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import love.forte.simbot.component.onebot.v11.core.event.notice.OneBotGroupMemberDecreaseEvent
import love.forte.simbot.component.onebot.v11.core.event.request.OneBotFriendRequestEvent
import love.forte.simbot.component.onebot.v11.core.event.request.OneBotGroupRequestEvent
import love.forte.simbot.event.RequestEvent
import love.forte.simbot.logger.LoggerFactory
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import tech.lq0.utils.*

@Component
class RequestProcess {

    val liveLogger = LoggerFactory.getLogger("LIVE")

    /**
     * 自动接受非黑名单用户的好友请求
     */
    @Listener
    suspend fun OneBotFriendRequestEvent.add() {
        // 拒绝黑名单用户的请求
        if (requesterId.toString() in botPermissionConfig.memberBlackList) {
            chatLogger.info("已拒绝 $requesterId 发起的好友请求(成员位于黑名单)")
            reject()
        } else {
            chatLogger.info("已同意 $requesterId 发起的好友请求")
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
            if (requesterId.toString() in botPermissionConfig.memberBlackList) {
                chatLogger.info("已拒绝 $requesterId(${requester().name}) 发起的邀请加群请求(成员位于黑名单): ${content().id}(${content().name})")
                return
            }

            // 拒绝加入黑名单群
            if (content().id.toString() in botPermissionConfig.groupBlackList) {
                chatLogger.info("已拒绝 $requesterId(${requester().name}) 发起的邀请加群请求(群位于黑名单): ${content().id}(${content().name})")
                reject()
            } else {
                chatLogger.info("已同意 $requesterId(${requester().name}) 发起的邀请加群请求: ${content().id}(${content().name})")
                accept()
            }
        }
    }

    @Listener
    suspend fun OneBotGroupMemberDecreaseEvent.removeConfig() {
        // 只处理bot自身被踢出的事件
        if (sourceEvent.subType != "kick_me") return

        val group = groupId.toString()
        chatLogger.info("已退出群 $group")

        // bot被踢出群时，清空该群的相关配置文件

        // 清除开播通知订阅信息
        val uidList = liveUIDBind.filter { group in it.value }.onEach {
            it.value -= group
            if (it.value.isEmpty()) liveUIDBind -= it.key
        }
        if (uidList.isNotEmpty()) {
            saveConfig("LiveNotify", "liveUIDBind.json", Json.encodeToString(liveUIDBind))
        }

        if (group in liveGroupConfig) {
            liveGroupConfig -= group
            saveConfig("LiveNotify", "liveGroupConfig.json", Json.encodeToString(liveGroupConfig))
        }

        liveLogger.info("已退出群 $group ，清空了该群订阅的${uidList.size}个主播: ${uidList.map { getUIDNameString(it.key) }}")
    }
}