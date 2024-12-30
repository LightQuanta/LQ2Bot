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
    val admin: MutableSet<String> = mutableSetOf(),
    val groupBlackList: MutableSet<String> = mutableSetOf(),
    val groupDisabledList: MutableSet<String> = mutableSetOf(),
    val memberBlackList: MutableSet<String> = mutableSetOf(),
)

val botPermissionConfig by lazy {
    readJSONConfigAs<UserPermissionConfig>("BotConfig", "permission.json") ?: UserPermissionConfig()
}

@Serializable
enum class DetectType {
    EQUAL, REGEX_MATCH, REGEX_REPLACE, STARTS_WITH
}

@Serializable
data class SingleMeme(
    val detectType: DetectType = DetectType.EQUAL,
    val name: String,
    var alias: MutableSet<String>? = mutableSetOf(),
    val replyContent: LinkedHashSet<String> = linkedSetOf(),
    val whiteListGroups: MutableSet<String>? = mutableSetOf(),
    val blackListGroups: MutableSet<String>? = mutableSetOf(),
)

@Serializable
data class Meme(
    var lastUpdateTime: Long = 0,
    val admin: MutableSet<String> = mutableSetOf(),
    val notificationReceiver: MutableSet<String> = mutableSetOf(),
    val memes: MutableList<SingleMeme> = mutableListOf(),
)

val memeConfig by lazy {
    readJSONConfigAs<Meme>("Meme", "meme.json") ?: Meme()
}