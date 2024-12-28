package tech.lq0.plugins

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import tech.lq0.interceptor.ChinesePunctuationReplace
import tech.lq0.interceptor.RequireAdmin
import tech.lq0.utils.PluginConfig
import tech.lq0.utils.directlySend
import tech.lq0.utils.groupPluginConfig
import tech.lq0.utils.saveConfig

@Component
class GroupSwitchControl {

    @Listener
    @ChinesePunctuationReplace
    @RequireAdmin
    @Filter("!{{operation,(enable|disable|reset)}} {{plugin,\\w+}}")
    suspend fun OneBotGroupMessageEvent.control(
        @FilterValue("operation") operation: String,
        @FilterValue("plugin") pluginID: String,
    ) {
        val groupID = content().id.toString()
        if (groupPluginConfig[groupID] == null) {
            groupPluginConfig[groupID] = PluginConfig()
        }

        when (operation) {
            "reset" -> {
                groupPluginConfig[groupID]!!.enabled.remove(pluginID)
                groupPluginConfig[groupID]!!.disabled.remove(pluginID)
                directlySend("已重置插件$pluginID")
            }

            "enable" -> {
                groupPluginConfig[groupID]!!.enabled.add(pluginID)
                groupPluginConfig[groupID]!!.disabled.remove(pluginID)
                directlySend("已启用插件$pluginID")
            }

            "disable" -> {
                groupPluginConfig[groupID]!!.disabled.add(pluginID)
                groupPluginConfig[groupID]!!.enabled.remove(pluginID)
                directlySend("已禁用插件$pluginID")
            }
        }

        saveConfig("PluginSwitch", "config.json", Json.encodeToString(groupPluginConfig))
    }

    @Listener
    @ChinesePunctuationReplace
    @Filter("!plugins")
    suspend fun OneBotGroupMessageEvent.list() = directlySend(
        // TODO 正确实现启用插件展示
        """
            当前插件列表
            已启用：${groupPluginConfig[content().id.toString()]?.enabled?.joinToString() ?: "无"}
            已禁用：${groupPluginConfig[content().id.toString()]?.disabled?.joinToString() ?: "无"}
        """.trimIndent()
    )

}
