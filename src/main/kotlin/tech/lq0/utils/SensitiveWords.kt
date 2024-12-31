package tech.lq0.utils

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import love.forte.simbot.component.onebot.v11.core.actor.OneBotGroup
import love.forte.simbot.logger.LoggerFactory
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

val groupViolationCount by lazy {
    readJSONConfigAs<MutableMap<String, Int>>("SensitiveWords", "violation.json") ?: mutableMapOf()
}

val sensitiveWordsRegex by lazy {
    readConfig("SensitiveWords", "sensitivewords.txt")
        .split("\n")
        .map { Regex(it, RegexOption.IGNORE_CASE) }
}

val chineseCharRegex = Regex("[一-龥]")
val pinyinFormat = with(HanyuPinyinOutputFormat()) {
    caseType = HanyuPinyinCaseType.LOWERCASE
    toneType = HanyuPinyinToneType.WITHOUT_TONE
    vCharType = HanyuPinyinVCharType.WITH_V
    return@with this
}

val logger = LoggerFactory.getLogger("banLogger")

/**
 * 拉黑指定成员
 * @param member 成员ID
 * @param group （可选）群号，累计三次违规自动拉黑对应群聊
 */
suspend fun banMember(member: String, group: OneBotGroup?) {
    if (member !in botPermissionConfig.admin) {
        botPermissionConfig.memberBlackList.add(member)
        logger.info("已拉黑用户QQ: $member")
    }

    if (group != null) {
        val count = (groupViolationCount[group.id.toString()] ?: 0) + 1
        groupViolationCount[group.id.toString()] = count
        saveConfig("SensitiveWords", "violation.json", Json.encodeToString(groupViolationCount))
        logger.info("群 $group 已累计触发敏感词 $count 次")

        if (count >= 3) {
            botPermissionConfig.groupBlackList += group.id.toString()
            group.send("该群由于多次触发敏感词已被Bot永久拉黑，请联系Bot管理员进行进一步操作")
            logger.info("群 $group 已被永久拉黑")
        }
    }
    saveConfig("BotConfig", "permission.json", Json.encodeToString(botPermissionConfig))
}

fun String?.isSensitive(): Boolean {
    if (isNullOrEmpty()) return false

    // 将数字替换为汉字，方便后续拼音检测
    val numberReplaced = this
        .replace('0', '零')
        .replace('1', '一')
        .replace('2', '二')
        .replace('3', '三')
        .replace('4', '四')
        .replace('5', '五')
        .replace('6', '六')
        .replace('7', '七')
        .replace('8', '八')
        .replace('9', '九')
    if (sensitiveWordsRegex.any { it.containsMatchIn(numberReplaced) }) return true

    // 拼音检测，防止谐音绕过
    val chineseChars = chineseCharRegex.findAll(numberReplaced).map { it.value[0] }.toList()
    val pinyin = buildMap {
        chineseChars.forEach {
            set(it, PinyinHelper.toHanyuPinyinStringArray(it, pinyinFormat).getOrElse(0) { c -> c })
        }
    }
    val replaced = numberReplaced.map { pinyin[it] ?: it }.joinToString("")
    return sensitiveWordsRegex.any { it.containsMatchIn(replaced) }
}