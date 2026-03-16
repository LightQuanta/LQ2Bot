package tech.lq0.plugins

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import tech.lq0.interceptor.ChinesePunctuationReplace
import tech.lq0.interceptor.RequireAdmin
import tech.lq0.utils.PluginConfig
import tech.lq0.utils.chatLogger
import tech.lq0.utils.directlySend
import tech.lq0.utils.groupPluginConfig

@Component
class GroupSwitchControl {

    @Listener
    @ChinesePunctuationReplace
    @RequireAdmin
    @Filter("!{{operation,(enable|disable|reset)}} {{plugins,\\w+([\\s,]+\\w+)*}}")
    suspend fun OneBotNormalGroupMessageEvent.control(
        @FilterValue("operation") operation: String,
        @FilterValue("plugins") pluginIDStr: String,
    ) {
        val groupID = content().id.toString()
        val config = groupPluginConfig.get(this) ?: PluginConfig()

        val pluginIDs = pluginIDStr.split(Regex("[\\s,]+")).distinct()
        for (pluginID in pluginIDs) {
            when (operation) {
                "reset" -> {
                    config.enabled -= pluginID
                    config.disabled -= pluginID
                }

                "enable" -> {
                    config.enabled += pluginID
                    config.disabled -= pluginID
                }

                "disable" -> {
                    config.disabled += pluginID
                    config.enabled -= pluginID
                }
            }
        }
        directlySend(
            when (operation) {
                "reset" -> "已重置${pluginIDs.joinToString()}"
                "enable" -> "已启用${pluginIDs.joinToString()}"
                "disable" -> "已禁用${pluginIDs.joinToString()}"
                else -> "未知操作"
            }
        )
        chatLogger.info("群 ${groupID}(${content().name}) $authorId(${author().name}) 进行了以下操作: !$operation ${pluginIDs.joinToString()}")

        groupPluginConfig.save()
    }

    @Listener
    @ChinesePunctuationReplace
    @Filter("!plugins")
    suspend fun OneBotNormalGroupMessageEvent.list() {
        val config = groupPluginConfig.get(this) ?: PluginConfig()

        directlySend(
            // TODO 正确实现启用插件展示
            """
            当前插件列表
            已启用: ${config.enabled.joinToString().ifEmpty { "无" }}
            已禁用: ${config.disabled.joinToString().ifEmpty { "无" }}
        """.trimIndent()
        )
    }

}
