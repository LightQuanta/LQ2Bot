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
import tech.lq0.utils.*

@Component
class BlackListControl {

    @Listener
    @ChinesePunctuationReplace
    @RequireBotAdmin
    @Filter("!{{operation,(un)?ban}} {{list,\\d+(\\D+\\d+)*}}")
    suspend fun OneBotMessageEvent.ban(
        @FilterValue("operation") operation: String,
        @FilterValue("list") banList: String,
    ) {
        // 禁止bot管理员拉黑bot管理员
        val list = banList.split(Regex("\\D+")).filter { it !in botPermissionConfig.admin }
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
                "ban" -> "已拉黑QQ ${list.joinToString()}"
                "unban" -> "已解封QQ ${list.joinToString()}"
                else -> "未知操作"
            }
        )
        banLogger.info(
            when (operation) {
                "ban" -> "$authorId 已拉黑QQ ${list.joinToString()}"
                "unban" -> "$authorId 已解封QQ ${list.joinToString()}"
                else -> "未知操作"
            }
        )
    }

    /**
     * 批量永久拉黑群聊，仅bot管理员可用
     */
    @Listener
    @ChinesePunctuationReplace
    @RequireBotAdmin
    @Filter("!{{operation,(un)?bangroup}} {{list,\\d+(\\D+\\d+)*}}")
    suspend fun OneBotMessageEvent.banGroup(
        @FilterValue("operation") operation: String,
        @FilterValue("list") banList: String,
    ) {
        val list = banList.split(Regex("\\D+"))
        for (id in list) {
            if (operation == "bangroup") {
                botPermissionConfig.groupBlackList += id
            } else {
                botPermissionConfig.groupBlackList -= id
                groupViolationCount -= id
                saveConfig("SensitiveWords", "violation.json", Json.encodeToString(groupViolationCount))
            }
        }
        saveConfig("BotConfig", "permission.json", Json.encodeToString(botPermissionConfig))

        directlySend(
            when (operation) {
                "bangroup" -> "已拉黑群 ${list.joinToString()}"
                "unbangroup" -> "已解封群 ${list.joinToString()}"
                else -> "未知操作"
            }
        )
        banLogger.info(
            when (operation) {
                "bangroup" -> "$authorId 已拉黑群 ${list.joinToString()}"
                "unbangroup" -> "$authorId 已解封群 ${list.joinToString()}"
                else -> "未知操作"
            }
        )
    }

    /**
     * 批量在群里启用/禁用bot功能，仅限bot管理员可用
     */
    @Listener
    @ChinesePunctuationReplace
    @RequireBotAdmin
    @Filter("!{{operation,(enable|disable)bot}} {{list,\\d+(\\D+\\d+)*}}")
    suspend fun OneBotMessageEvent.batchDisableGroup(
        @FilterValue("operation") operation: String,
        @FilterValue("list") banList: String,
    ) {
        val list = banList.split(Regex("\\D+"))
        for (id in list) {
            if (operation == "enablebot") {
                botPermissionConfig.groupDisabledList -= id
            } else {
                botPermissionConfig.groupDisabledList += id
            }
        }
        saveConfig("BotConfig", "permission.json", Json.encodeToString(botPermissionConfig))

        directlySend(
            when (operation) {
                "enablebot" -> "已在群 ${list.joinToString()} 中禁用bot"
                "disablebot" -> "已在群 ${list.joinToString()} 中启用bot"
                else -> "未知操作"
            }
        )
        banLogger.info(
            when (operation) {
                "enablebot" -> "$authorId 已在群 ${list.joinToString()} 中禁用bot"
                "disablebot" -> "$authorId 已在群 ${list.joinToString()} 中启用bot"
                else -> "未知操作"
            }
        )
    }

    /**
     * 在当前群启用/禁用bot功能，群管理员和bot管理员可用
     */
    @Listener
    @ChinesePunctuationReplace
    @RequireAdmin
    @Filter("!{{operation,(enable|disable)bot}}")
    suspend fun OneBotGroupMessageEvent.disableGroup(@FilterValue("operation") operation: String) {
        val groupID = content().id.toString()
        if (operation == "enablebot") {
            botPermissionConfig.groupDisabledList -= groupID
        } else {
            botPermissionConfig.groupDisabledList += groupID
        }

        saveConfig("BotConfig", "permission.json", Json.encodeToString(botPermissionConfig))

        directlySend(
            when (operation) {
                "enablebot" -> "已在此群启用bot"
                "disablebot" -> "已在此群禁用bot"
                else -> "未知操作"
            }
        )
        banLogger.info(
            when (operation) {
                "enablebot" -> "$authorId 在群 $groupID 中启用bot"
                "disablebot" -> "$authorId 在 $groupID 中禁用bot"
                else -> "未知操作"
            }
        )
    }

}
