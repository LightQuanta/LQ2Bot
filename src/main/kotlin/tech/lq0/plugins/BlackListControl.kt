package tech.lq0.plugins

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotMessageEvent
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import tech.lq0.interceptor.ChinesePunctuationReplace
import tech.lq0.interceptor.RequireAdmin
import tech.lq0.interceptor.RequireBotAdmin
import tech.lq0.utils.botPermissionConfig
import tech.lq0.utils.directlySend
import tech.lq0.utils.saveConfig

@Component
class BlackListControl {

    @Listener
    @ChinesePunctuationReplace
    @RequireBotAdmin
    @Filter("!{{operation,(un)?ban}} {{list,\\d+([, ]\\d+)*}}")
    suspend fun OneBotMessageEvent.ban(
        @FilterValue("operation") operation: String,
        @FilterValue("list") banList: String,
    ) {
        // 禁止拉黑bot管理员
        val list = banList.split(",", " ").filter { it !in botPermissionConfig.admin }
        for (id in list) {
            if (operation == "ban") {
                botPermissionConfig.memberBlackList += id
            } else {
                botPermissionConfig.memberBlackList -= id
            }
        }
        saveConfig("BotConfig", "permission.json", Json.encodeToString(botPermissionConfig))

        directlySend(
            when (operation) {
                "ban" -> "已拉黑QQ: ${list.joinToString()}"
                "unban" -> "已解封QQ: ${list.joinToString()}"
                else -> "未知操作"
            }
        )
    }

    @Listener
    @ChinesePunctuationReplace
    @RequireBotAdmin
    @Filter("!{{operation,(un)?bangroup}} {{list,\\d+([, ]\\d+)*}}")
    suspend fun OneBotMessageEvent.banGroup(
        @FilterValue("operation") operation: String,
        @FilterValue("list") banList: String,
    ) {
        val list = banList.split(",", " ")
        for (id in list) {
            if (operation == "bangroup") {
                botPermissionConfig.groupBlackList += id
            } else {
                botPermissionConfig.groupBlackList -= id
            }
        }
        saveConfig("BotConfig", "permission.json", Json.encodeToString(botPermissionConfig))

        directlySend(
            when (operation) {
                "bangroup" -> "已拉黑QQ群: ${list.joinToString()}"
                "unbangroup" -> "已解封QQ群: ${list.joinToString()}"
                else -> "未知操作"
            }
        )
    }

    @Listener
    @ChinesePunctuationReplace
    @RequireAdmin
    @Filter("!{{operation,(enable|disable)bot}}")
    suspend fun OneBotGroupMessageEvent.enableDisable(@FilterValue("operation") operation: String) {
        val groupID = content().id.toString()
        if (operation == "enablebot") {
            botPermissionConfig.groupBlackList -= groupID
        } else {
            botPermissionConfig.groupBlackList += groupID
        }

        saveConfig("BotConfig", "permission.json", Json.encodeToString(botPermissionConfig))

        directlySend(
            when (operation) {
                "enablebot" -> "已在此群启用bot"
                "disablebot" -> "已在此群禁用bot"
                else -> "未知操作"
            }
        )
    }

}
