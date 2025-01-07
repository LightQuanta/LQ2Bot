package tech.lq0.interceptor

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.event.EventInterceptor
import love.forte.simbot.event.EventResult
import love.forte.simbot.quantcat.common.annotations.Interceptor
import love.forte.simbot.quantcat.common.interceptor.AnnotationEventInterceptorFactory
import love.forte.simbot.spring.configuration.listener.KFunctionEventListener
import tech.lq0.utils.botPermissionConfig
import tech.lq0.utils.groupPluginConfig
import kotlin.reflect.full.findAnnotation


/**
 * 控制插件是否可以被开关
 */
@Target(AnnotationTarget.FUNCTION)
@Interceptor(GroupSwitchFactory::class)
@Retention(AnnotationRetention.RUNTIME)
annotation class FunctionSwitch(
    /**
     * 插件ID
     */
    val value: String,
    /**
     * 是否默认启用该插件
     */
    val defaultEnabled: Boolean = true,
)

data object GroupSwitchFactory : AnnotationEventInterceptorFactory {
    override fun create(context: AnnotationEventInterceptorFactory.Context): AnnotationEventInterceptorFactory.Result {
        return AnnotationEventInterceptorFactory.Result.build {
            interceptor(GroupSwitchInterceptor)
            configuration { priority = context.priority }
        }
    }

    private data object GroupSwitchInterceptor : EventInterceptor {
        override suspend fun EventInterceptor.Context.intercept(): EventResult {
            val event = eventListenerContext.event

            // 禁止黑名单用户使用功能
            if (event is OneBotMessageEvent && event.authorId.toString() in botPermissionConfig.memberBlackList) {
                return EventResult.invalid()
            }
            // 私聊消息无需管理是否启用
            if (event !is OneBotGroupMessageEvent) return invoke()

            // 禁止黑名单群使用功能
            if (event.groupId.toString() in botPermissionConfig.groupBlackList) {
                return EventResult.invalid()
            }

            // 查找GroupSwitch注解
            val listener = eventListenerContext.listener
            val functionSwitch = if (listener is KFunctionEventListener) {
                listener.caller.findAnnotation<FunctionSwitch>()
            } else {
                null
            }

            val defaultEnabled = functionSwitch?.defaultEnabled ?: true
            val pluginID = functionSwitch?.value ?: return EventResult.invalid()

            val groupConfig = groupPluginConfig[event.groupId.toString()]
            // 若禁用此插件，则不进行处理
            if (pluginID in (groupConfig?.disabled ?: setOf())) return EventResult.invalid()

            // 查看插件是否明确启用，否则使用插件默认设置
            val enabled = pluginID in (groupConfig?.enabled ?: setOf())
            return if (enabled || defaultEnabled) {
                invoke()
            } else {
                EventResult.invalid()
            }

        }
    }
}