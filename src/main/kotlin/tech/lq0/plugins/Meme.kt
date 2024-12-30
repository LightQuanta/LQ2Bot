package tech.lq0.plugins

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import tech.lq0.interceptor.ChinesePunctuationReplace
import tech.lq0.interceptor.FunctionSwitch
import tech.lq0.utils.DetectType
import tech.lq0.utils.directlySend
import tech.lq0.utils.memeConfig

@Component
class Meme {

    @Listener
    @FunctionSwitch("Meme")
    @ChinesePunctuationReplace
    suspend fun OneBotMessageEvent.meme() {
        val text = this.messageContent.plainText
        if (text.isNullOrEmpty()) return

        memeConfig.memes.firstOrNull {
            listOf(it.name, *it.alias?.toTypedArray() ?: emptyArray())
                .any { keyword ->
                    when (it.detectType) {
                        DetectType.EQUAL -> text == keyword
                        DetectType.STARTS_WITH -> text.startsWith(keyword)
                        DetectType.REGEX_MATCH,
                        DetectType.REGEX_REPLACE -> text.matches(Regex(keyword, RegexOption.IGNORE_CASE))
                    }
                }
        }?.let {
            if (it.detectType != DetectType.REGEX_REPLACE) {
                directlySend(it.replyContent.random())
            } else {
                // TODO 敏感词检测
                val regex = Regex(it.name, RegexOption.IGNORE_CASE)
                val index = regex.matchEntire(text)
                    ?.groups
                    ?.get("id")
                    ?.value
                    ?.toIntOrNull()
                    ?.coerceIn(1..it.replyContent.size)

                directlySend(
                    regex.replace(
                        text,
                        if (index != null) {
                            it.replyContent.toList()[index - 1]
                        } else {
                            it.replyContent.random()
                        }
                    )
                )
            }
        }
    }

}
