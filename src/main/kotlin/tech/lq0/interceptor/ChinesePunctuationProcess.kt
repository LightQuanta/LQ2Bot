package tech.lq0.interceptor

import love.forte.simbot.event.EventInterceptor
import love.forte.simbot.event.EventResult
import love.forte.simbot.quantcat.common.annotations.Interceptor
import love.forte.simbot.quantcat.common.interceptor.AnnotationEventInterceptorFactory


/**
 * 将所有输入的文字中的中文标点转换为英文标点
 */
@Target(AnnotationTarget.FUNCTION)
@Interceptor(ChinesePunctuationConversionFactory::class)
annotation class ChinesePunctuationReplace

data object ChinesePunctuationConversionFactory : AnnotationEventInterceptorFactory {
    override fun create(context: AnnotationEventInterceptorFactory.Context): AnnotationEventInterceptorFactory.Result {
        return AnnotationEventInterceptorFactory.Result.build {
            interceptor(ChinesePunctuationInterceptor)
            configuration {
                priority = context.priority
            }
        }
    }

    private data object ChinesePunctuationInterceptor : EventInterceptor {
        override suspend fun EventInterceptor.Context.intercept(): EventResult {
            with(eventListenerContext) {
                plainText = plainText
                    ?.replace('，', ',')
                    ?.replace('。', '.')
                    ?.replace('？', '?')
                    ?.replace('“', '\"')
                    ?.replace('”', '\"')
                    ?.replace('！', '!')
                    ?.replace('：', ':')
                    ?.replace('；', ';')
                    ?.replace('（', '(')
                    ?.replace('）', ')')
                    ?.replace('《', '<')
                    ?.replace('》', '>')
                    ?.replace('‘', '\'')
                    ?.replace('’', '\'')
                    ?.replace('【', '[')
                    ?.replace('】', ']')
                    ?.replace('、', ',')
                    ?.replace('‘', '\'')
                    ?.replace('’', '\'')
            }

            return invoke()
        }
    }
}