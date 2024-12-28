package tech.lq0.interceptor

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.event.EventInterceptor
import love.forte.simbot.event.EventResult
import love.forte.simbot.quantcat.common.annotations.Interceptor
import love.forte.simbot.quantcat.common.interceptor.AnnotationEventInterceptorFactory
import tech.lq0.utils.userPermissionConfig


/**
 * 要求该功能只能由群管理员或Bot管理员使用
 */
@Target(AnnotationTarget.FUNCTION)
@Interceptor(RequireAdminFactory::class)
annotation class RequireAdmin

data object RequireAdminFactory : AnnotationEventInterceptorFactory {
    override fun create(context: AnnotationEventInterceptorFactory.Context): AnnotationEventInterceptorFactory.Result {
        return AnnotationEventInterceptorFactory.Result.build {
            interceptor(RequireAdminInterceptor)
            configuration { priority = context.priority }
        }
    }

    private data object RequireAdminInterceptor : EventInterceptor {
        override suspend fun EventInterceptor.Context.intercept(): EventResult {
            val event = eventListenerContext.event

            return if (
                event is OneBotMessageEvent && userPermissionConfig.admin.contains(event.authorId.toString())
                || event is OneBotNormalGroupMessageEvent && event.author().role?.isAdmin == true
            ) {
                invoke()
            } else {
                EventResult.invalid()
            }
        }
    }
}