package tech.lq0.utils

import love.forte.simbot.event.Event
import tech.lq0.interceptor.hasAdminPermission
import tech.lq0.interceptor.hasBotAdminPermission

enum class CommandPermission(val description: String) {
    ALL("无"),
    ADMIN("群管理员、群主或Bot管理员"),
    BOT_ADMIN("Bot管理员"),
}

suspend fun hasPermission(event: Event, permission: CommandPermission): Boolean {
    return when (permission) {
        CommandPermission.ALL -> true
        CommandPermission.ADMIN -> hasAdminPermission(event)
        CommandPermission.BOT_ADMIN -> hasBotAdminPermission(event)
    }
}