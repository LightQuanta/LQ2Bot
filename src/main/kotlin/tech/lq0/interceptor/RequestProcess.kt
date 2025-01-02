package tech.lq0.interceptor

import love.forte.simbot.component.onebot.v11.core.event.request.OneBotFriendRequestEvent
import love.forte.simbot.component.onebot.v11.core.event.request.OneBotGroupRequestEvent
import love.forte.simbot.event.RequestEvent
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import tech.lq0.utils.botPermissionConfig

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
}