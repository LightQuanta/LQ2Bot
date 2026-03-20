package tech.lq0.interceptor

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.event.Event
import love.forte.simbot.event.EventInterceptor
import love.forte.simbot.event.EventResult
import love.forte.simbot.quantcat.common.annotations.Interceptor
import love.forte.simbot.quantcat.common.interceptor.AnnotationEventInterceptorFactory
import tech.lq0.utils.botPermissionConfig


/**
 * 要求该功能只能由Bot管理员使用
 */
@Target(AnnotationTarget.FUNCTION)
@Interceptor(RequireBotAdminFactory::class)
annotation class RequireBotAdmin

data object RequireBotAdminFactory : AnnotationEventInterceptorFactory {
    override fun create(context: AnnotationEventInterceptorFactory.Context): AnnotationEventInterceptorFactory.Result {
        return AnnotationEventInterceptorFactory.Result.build {
            interceptor(RequireBotAdminInterceptor)
            configuration { priority = context.priority }
        }
    }

    private data object RequireBotAdminInterceptor : EventInterceptor {
        override suspend fun EventInterceptor.Context.intercept(): EventResult {
            val event = eventListenerContext.event

            return if (hasBotAdminPermission(event)) {
                invoke()
            } else {
//                if (event is OneBotMessageEvent) {
//                    event.directlySend("权限不足，需要${CommandPermission.BOT_ADMIN.description}权限")
//                }
                EventResult.invalid()
            }
        }
    }
}

fun hasBotAdminPermission(event: Event): Boolean {
    return event is OneBotMessageEvent && event.authorId.toString() in botPermissionConfig.get().admin
}