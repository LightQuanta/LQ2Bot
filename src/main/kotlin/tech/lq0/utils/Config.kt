package tech.lq0.utils

import kotlinx.serialization.Serializable

@Serializable
data class PluginConfig(
    val enabled: MutableSet<String> = mutableSetOf(),
    val disabled: MutableSet<String> = mutableSetOf(),
)

val groupPluginConfig by lazy {
    readJSONConfigAs<MutableMap<String, PluginConfig>>("PluginSwitch", "config.json") ?: mutableMapOf()
}

@Serializable
data class UserPermissionConfig(
    val admin: Set<String> = setOf(),
)

val userPermissionConfig by lazy {
    readJSONConfigAs<UserPermissionConfig>("BotConfig", "permission.json") ?: UserPermissionConfig()
}