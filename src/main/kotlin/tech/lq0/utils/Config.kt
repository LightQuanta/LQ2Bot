package tech.lq0.utils

import kotlinx.serialization.Serializable
import tech.lq0.plugins.RoomInfo
import java.util.*

// PluginSwitch部分

@Serializable
data class PluginConfig(
    val enabled: MutableSet<String> = mutableSetOf(),
    val disabled: MutableSet<String> = mutableSetOf(),
)

val groupPluginConfig by lazy {
    readJSONConfigAs<MutableMap<String, PluginConfig>>("PluginSwitch", "config.json") ?: mutableMapOf()
}

// BotConfig部分

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

// Meme部分

@Serializable
enum class DetectType {
    EQUAL, REGEX_MATCH, REGEX_REPLACE, STARTS_WITH
}

@Serializable
data class SingleMeme(
    val detectType: DetectType = DetectType.EQUAL,
    val name: String,
    val id: String = UUID.randomUUID().toString(),
    var alias: MutableSet<String> = mutableSetOf(),
    val replyContent: LinkedHashSet<String> = linkedSetOf(),
    val whiteListGroups: MutableSet<String> = mutableSetOf(),
    var blackListGroups: MutableSet<String> = mutableSetOf(),
) {
    /**
     * 判断该Meme在指定群聊是否可用
     */
    fun availableTo(group: String?): Boolean {
        if (whiteListGroups.isNotEmpty() && group !in whiteListGroups) return false
        if (blackListGroups.isNotEmpty() && group in blackListGroups) return false
        return true
    }
}

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

// LiveNotify部分

/**
 * 订阅主播直播间状态缓存
 * 主播UID -> 上次直播间状态
 */
val liveStateCache by lazy {
    readJSONConfigAs<MutableMap<String, RoomInfo>>("LiveNotify", "liveStateCache.json") ?: mutableMapOf()
}

/**
 * 开播通知订阅信息
 * 订阅主播UID -> Set<订阅群群号>
 */
val liveUIDBind by lazy {
    readJSONConfigAs<MutableMap<String, MutableSet<String>>>("LiveNotify", "liveUIDBind.json") ?: mutableMapOf()
}

@Serializable
data class LiveNotifyGroupConfig(
    /** 是否通知主播下播 */
    var notifyStopStream: Boolean = true,

    /** 是否在下播时播报直播时长 */
    var showStreamTime: Boolean = true,

    /** 是否显示直播标题 */
    var showTitle: Boolean = true,

    /** 是否显示直播间封面 */
    var showCover: Boolean = true,

    /** 是否显示直播间链接 */
    var showLink: Boolean = true,

    /** 是否显示直播分区 */
    var showLiveArea: Boolean = true,

    /** 是否在直播时通知直播标题更改 */
    var notifyTitleChangeWhileStreaming: Boolean = true,

    /** 是否在非直播时通知直播标题更改 */
    var notifyTitleChangeWhileNotStreaming: Boolean = false,
)

/**
 * 每个群的直播推送配置
 */
val liveGroupConfig by lazy {
    readJSONConfigAs<MutableMap<String, LiveNotifyGroupConfig>>("LiveNotify", "liveGroupConfig.json") ?: mutableMapOf()
}

/**
 * 疑似存在违规名称和直播间标题的主播的UID
 */
val sensitiveLivers by lazy {
    readJSONConfigAs<MutableSet<String>>("LiveNotify", "sensitiveLivers.json") ?: mutableSetOf()
}

/**
 * 主播UID -> 主播名称
 */
val UIDNameCache by lazy {
    readJSONConfigAs<MutableMap<String, String>>("Cache", "UID2Name.json") ?: mutableMapOf()
}

val alarms by lazy {
    readJSONConfigAs<MutableMap<String, MutableSet<String>>>("Alarm", "alarms.json") ?: mutableMapOf()
}

@Serializable
data class VTBsUIDCache(
    var lastUpdateTime: Long = 0,
    var uidList: MutableSet<Long> = mutableSetOf(),
)

// 管人列表
val VTBsCache by lazy {
    readJSONConfigAs<VTBsUIDCache>("DDTool", "vtbs.json") ?: VTBsUIDCache()
}

// 群强制单推信息
val ddToolBind by lazy {
    readJSONConfigAs<MutableMap<String, Long>>("DDTool", "bind.json") ?: mutableMapOf()
}