package tech.lq0.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import love.forte.simbot.event.ChatGroupEvent
import tech.lq0.plugins.RoomInfo
import java.util.*

open class BotConfig<T : Any>(
    val serializer: KSerializer<T>,
    var value: T,
    val componentName: String,
    val fileName: String,
    val autoBackup: Boolean = true,
) {
    init {
        try {
            @Suppress("unchecked_cast")
            value = Json.decodeFromString(serializer, readConfig(componentName, fileName))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun get() = value

    fun save() {
        saveConfig(componentName, fileName, Json.encodeToString(serializer, value), autoBackup)
    }
}

@Suppress("unchecked_cast")
class GroupConfig<T : MutableMap<String, V>, V : Any>(
    serializer: KSerializer<T>, componentName: String, fileName: String, autoBackup: Boolean = true
) : BotConfig<T>(serializer, mutableMapOf<String, V>() as T, componentName, fileName, autoBackup) {

    suspend fun get(event: ChatGroupEvent) = get(event.content().id.toString())
    suspend fun getOrPut(event: ChatGroupEvent, config: () -> V) = getOrPut(event.content().id.toString(), config)
    operator fun get(id: String) = value[id]
    fun getOrPut(id: String, config: () -> V) = value.getOrPut(id, config)

    operator fun set(id: String, value: V) {
        this.value[id] = value
    }

    operator fun contains(id: String) = value.contains(id)

    operator fun minusAssign(id: String) {
        value.remove(id)
    }
}

inline fun <reified T : Any> createGroupConfig(
    componentName: String,
    fileName: String,
    autoBackup: Boolean = true
) = GroupConfig<MutableMap<String, T>, T>(serializer(), componentName, fileName, autoBackup)

inline fun <reified T : Any> createBotConfig(
    defaultValue: T,
    componentName: String,
    fileName: String,
    autoBackup: Boolean = true
) = BotConfig(serializer(), defaultValue, componentName, fileName, autoBackup)

// PluginSwitch部分

@Serializable
data class PluginConfig(
    val enabled: MutableSet<String> = mutableSetOf(),
    val disabled: MutableSet<String> = mutableSetOf(),
)

val groupPluginConfig = createGroupConfig<PluginConfig>("PluginSwitch", "config.json")

// BotConfig部分

@Serializable
data class UserPermissionConfig(
    val admin: MutableSet<String> = mutableSetOf(),
    val groupBlackList: MutableSet<String> = mutableSetOf(),
    val groupDisabledList: MutableSet<String> = mutableSetOf(),
    val memberBlackList: MutableSet<String> = mutableSetOf(),
)

val botPermissionConfig = createBotConfig(UserPermissionConfig(), "BotConfig", "permission.json")

// Meme部分

@Serializable
enum class DetectType {
    EQUAL, REGEX_MATCH, REGEX_REPLACE, STARTS_WITH
}

@Serializable
data class SingleMeme(
    var detectType: DetectType = DetectType.EQUAL,
    var name: String,
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

val memeConfig = createBotConfig(Meme(), "Meme", "meme.json")

// LiveNotify部分

/**
 * 订阅主播直播间状态缓存
 * 主播UID -> 上次直播间状态
 */
val liveStateCache = createBotConfig(mutableMapOf<String, RoomInfo>(), "LiveNotify", "liveStateCache.json", false)

/**
 * 开播通知订阅信息
 * 订阅主播UID -> Set<订阅群群号>
 */
val liveUIDBind = createBotConfig(mutableMapOf<String, MutableSet<String>>(), "LiveNotify", "liveUIDBind.json")

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

    /** 神秘配置项 */
    var hazelTimeUnit: Boolean = false,
)

/**
 * 每个群的直播推送配置
 */
val liveGroupConfig = createGroupConfig<LiveNotifyGroupConfig>("LiveNotify", "liveGroupConfig.json")

/**
 * 疑似存在违规名称和直播间标题的主播的UID
 */
val sensitiveLivers = createBotConfig(mutableSetOf<String>(), "LiveNotify", "sensitiveLivers.json")

/**
 * 主播UID -> 主播名称
 */
val UIDNameCache = createBotConfig(mutableMapOf<String, String>(), "Cache", "UID2Name.json", false)

val alarms = createGroupConfig<MutableSet<String>>("Alarm", "alarms.json")

@Serializable
data class VtuberUIDCache(
    var lastUpdateTime: Long = 0,
    var uidList: MutableSet<Long> = mutableSetOf(),
)

// 管人列表
val vtuberCache = createBotConfig(VtuberUIDCache(), "DDTool", "vtbs.json", false)

// 群强制单推信息
val ddToolBind = createGroupConfig<Long>("DDTool", "bind.json")