package tech.lq0.interceptor

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.event.EventInterceptor
import love.forte.simbot.event.EventResult
import love.forte.simbot.quantcat.common.annotations.Interceptor
import love.forte.simbot.quantcat.common.interceptor.AnnotationEventInterceptorFactory
import love.forte.simbot.spring.configuration.listener.KFunctionEventListener
import tech.lq0.utils.groupPluginConfig
import kotlin.reflect.full.findAnnotation


/**
 * 控制插件是否在群聊里启用
 */
@Target(AnnotationTarget.FUNCTION)
@Interceptor(GroupSwitchFactory::class)
@Retention(AnnotationRetention.RUNTIME)
annotation class GroupSwitch(
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
            // 只处理群消息
            if (event !is OneBotGroupMessageEvent) return invoke()

            // 查找GroupSwitch注解
            val listener = eventListenerContext.listener
            val groupSwitch = if (listener is KFunctionEventListener) {
                listener.caller.findAnnotation<GroupSwitch>()
            } else {
                null
            }

            val defaultEnabled = groupSwitch?.defaultEnabled ?: true
            val pluginID = groupSwitch?.value ?: return EventResult.invalid()

            val groupConfig = groupPluginConfig[event.groupId.toString()]
            if (groupConfig?.disabled?.contains(pluginID) == true) return EventResult.invalid()

            val enabled = groupConfig?.enabled?.contains(pluginID) ?: defaultEnabled
            return if (enabled) {
                invoke()
            } else {
                EventResult.invalid()
            }

        }
    }
}