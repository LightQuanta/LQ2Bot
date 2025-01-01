package tech.lq0.plugins

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.quantcat.common.annotations.Filter
import love.forte.simbot.quantcat.common.annotations.FilterValue
import love.forte.simbot.quantcat.common.annotations.Listener
import org.springframework.stereotype.Component
import tech.lq0.interceptor.FunctionSwitch
import tech.lq0.utils.alarms
import tech.lq0.utils.directlySend
import java.time.Instant

@Component
class Alarm {

    @Listener
    @FunctionSwitch("Alarm")
    @Filter("警钟(长鸣|敲烂)?{{id,(\\d+)?}}")
    suspend fun OneBotGroupMessageEvent.alarm(@FilterValue("id") id: String) {
        val alarmList = alarms[groupId.toString()]
        if (alarmList.isNullOrEmpty()) {
            directlySend("本群还没有警钟！")
            return
        }

        val alarmMessage = if (id.isEmpty()) {
            alarmList.last()
        } else {
            alarmList.toList()[(id.toInt() - 1).coerceIn(alarmList.indices)]
        }

        val currentInstant = Instant.now()
        val nanoTimestamp = currentInstant.toEpochMilli() * 1_000_000 + currentInstant.nano % 1000_000

        val alarmTimestamp = alarmMessage.substringBefore('|').toLong() * 1_000_000
        val message = alarmMessage.substringAfter('|')

        directlySend(message.replace("\${time}", getTimeDiffStr(alarmTimestamp, nanoTimestamp)))
    }

    @Listener
    @FunctionSwitch("Alarm")
    @Filter("编钟")
    suspend fun OneBotGroupMessageEvent.allAlarm() {
        val alarmList = alarms[groupId.toString()]
        if (alarmList.isNullOrEmpty()) {
            directlySend("本群还没有警钟！")
            return
        }

        val currentInstant = Instant.now()
        val nanoTimestamp = currentInstant.toEpochMilli() * 1_000_000 + currentInstant.nano % 1000_000

        directlySend(
            alarmList.joinToString("\n") {
                val alarmTimestamp = it.substringBefore('|').toLong() * 1_000_000
                val message = it.substringAfter('|')
                message.replace("\${time}", getTimeDiffStr(alarmTimestamp, nanoTimestamp))
            }
        )
    }

    fun getTimeDiffStr(past: Long, now: Long): String {
        val totalNanoDiff = now - past
        val dayDiff = totalNanoDiff / 86_400_000_000_000
        val hourDiff = (totalNanoDiff % 86_400_000_000_000) / 3_600_000_000_000
        val minuteDiff = (totalNanoDiff % 3_600_000_000_000) / 60_000_000_000
        val secondDiff = (totalNanoDiff % 60_000_000_000) / 1_000_000_000
        val milliDiff = (totalNanoDiff % 1_000_000_000) / 1_000_000
        val microDiff = (totalNanoDiff % 1_000_000) / 1000
        val nanoDiff = milliDiff % 1000
        return "${dayDiff}天${hourDiff}小时${minuteDiff}分${secondDiff}秒${milliDiff}毫秒${microDiff}微秒${nanoDiff}纳秒"
    }

}
