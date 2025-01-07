package tech.lq0.plugins

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import tech.lq0.interceptor.ChinesePunctuationReplace
import tech.lq0.interceptor.RequireAdmin
import tech.lq0.utils.*

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
        if (groupPluginConfig[groupID] == null) {
            groupPluginConfig[groupID] = PluginConfig()
        }

        val pluginIDs = pluginIDStr.split(Regex("[\\s,]+")).distinct()
        for (pluginID in pluginIDs) {
            when (operation) {
                "reset" -> {
                    groupPluginConfig[groupID]!!.enabled -= pluginID
                    groupPluginConfig[groupID]!!.disabled -= pluginID
                }

                "enable" -> {
                    groupPluginConfig[groupID]!!.enabled += pluginID
                    groupPluginConfig[groupID]!!.disabled -= pluginID
                }

                "disable" -> {
                    groupPluginConfig[groupID]!!.disabled += pluginID
                    groupPluginConfig[groupID]!!.enabled -= pluginID
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

        saveConfig("PluginSwitch", "config.json", Json.encodeToString(groupPluginConfig))
    }

    @Listener
    @ChinesePunctuationReplace
    @Filter("!plugins")
    suspend fun OneBotGroupMessageEvent.list() = directlySend(
        // TODO 正确实现启用插件展示
        """
            当前插件列表
            已启用: ${groupPluginConfig[content().id.toString()]?.enabled?.joinToString() ?: "无"}
            已禁用: ${groupPluginConfig[content().id.toString()]?.disabled?.joinToString() ?: "无"}
        """.trimIndent()
    )

}
