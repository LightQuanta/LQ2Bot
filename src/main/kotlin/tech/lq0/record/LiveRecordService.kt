package tech.lq0.record

import org.springframework.stereotype.Service
import tech.lq0.utils.liveLogger
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 直播记录查询结果
 */
data class LiveStats(
    /** 累计开播次数 */
    val totalLiveCount: Long = 0,
    /** 累计开播时长（秒） */
    val totalLiveDuration: Long = 0,
    /** 历史标题（最近若干条去重） */
    val historicalTitles: List<String> = emptyList(),
    /** 开始记录时间（秒级时间戳） */
    val recordStartTime: Long = 0,
    /** 当月开播次数 */
    val monthlyLiveCount: Long = 0,
    /** 当月开播时长（秒） */
    val monthlyLiveDuration: Long = 0,
    /** 当月历史标题 */
    val monthlyTitles: List<String> = emptyList(),
    /** 是否正在直播 */
    val isCurrentlyLive: Boolean = false,
    /** 当前直播已持续时间（秒），仅正在直播时有意义 */
    val currentLiveDuration: Long = 0,
) {
    val totalLiveDurationFormatted: String get() = formatDuration(totalLiveDuration)
    val monthlyLiveDurationFormatted: String get() = formatDuration(monthlyLiveDuration)
    val recordStartTimeFormatted: String
        get() {
            if (recordStartTime == 0L) return "暂无记录"
            val instant = Instant.ofEpochSecond(recordStartTime)
            val dateTime = LocalDateTime.ofInstant(instant, ZoneId.of("Asia/Shanghai"))
            return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }
    val currentLiveDurationFormatted: String get() = formatDuration(currentLiveDuration)

    companion object {
        fun formatDuration(totalSeconds: Long): String {
            if (totalSeconds <= 0) return "暂无"
            val days = totalSeconds / (60 * 60 * 24)
            val hours = totalSeconds % (60 * 60 * 24) / (60 * 60)
            val minutes = totalSeconds % (60 * 60) / 60
            return buildString {
                if (days > 0) append("${days}天")
                if (hours > 0) append("${hours}小时")
                if (minutes > 0 || (days == 0L && hours == 0L)) append("${minutes}分钟")
            }
        }
    }
}

@Service
class LiveRecordService(private val repository: LiveRecordRepository) {

    /**
     * 记录一条直播状态变更事件
     * @param uid 主播UID
     * @param roomId 直播间ID
     * @param eventType 事件类型（LIVE_START / LIVE_STOP / TITLE_CHANGE）
     * @param title 当前直播间标题
     * @param previousTitle 变更前标题（仅TITLE_CHANGE时非空）
     * @param liveStartTime B站API返回的开播时间戳（秒）
     * @param recordTime 记录时间戳（秒）
     * @param timeDiff 与上一条记录的时间差（秒）
     */
    fun recordEvent(
        uid: Long,
        roomId: Long,
        eventType: String,
        title: String = "",
        previousTitle: String = "",
        liveStartTime: Long = 0,
        recordTime: Long,
        timeDiff: Long = 0,
    ): LiveRecord {
        val month = LiveRecord.monthOf(recordTime)
        val record = LiveRecord(
            uid = uid,
            roomId = roomId,
            eventType = eventType,
            title = title,
            previousTitle = previousTitle,
            liveStartTime = liveStartTime,
            recordTime = recordTime,
            timeDiff = timeDiff,
            month = month,
        )
        val saved = repository.save(record)
        liveLogger.info("已记录直播事件: UID=$uid, 类型=$eventType, 标题=$title, recordTime=$recordTime, timeDiff=${timeDiff}s")
        return saved
    }

    /**
     * 获取某主播上一条记录（用于计算时间差）
     */
    fun getLastRecord(uid: Long): LiveRecord? = repository.findTopByUidOrderByRecordTimeDesc(uid)

    /**
     * 查询某主播的累计统计信息
     */
    fun getStats(uid: Long, currentTime: Long = System.currentTimeMillis() / 1000): LiveStats {
        val records = repository.findByUidOrderByRecordTimeAsc(uid)
        if (records.isEmpty()) return LiveStats()

        val firstRecord = records.first()
        val lastRecord = records.last()

        // 计算累计开播次数
        val totalLiveCount = records.count { it.eventType == EventType.LIVE_START }.toLong()

        // 计算累计开播时长：将 LIVE_START 与紧随的 LIVE_STOP 配对
        val totalLiveDuration = calculateDuration(records, currentTime)

        // 历史标题（去重，最近50条）
        val historicalTitles = records
            .filter { it.title.isNotEmpty() }
            .map { it.title }
            .distinct()
            .takeLast(50)

        // 当月统计
        val currentMonth = LiveRecord.monthOf(currentTime)
        val monthlyLiveCount = records.count {
            it.eventType == EventType.LIVE_START && it.month == currentMonth
        }.toLong()
        val monthlyLiveDuration = calculateMonthlyDuration(records, currentMonth, currentTime)
        val monthlyTitles = records
            .filter { it.month == currentMonth && it.title.isNotEmpty() }
            .map { it.title }
            .distinct()
            .takeLast(30)

        // 是否正在直播
        val isCurrentlyLive = lastRecord.eventType == EventType.LIVE_START
        val currentLiveDuration = if (isCurrentlyLive && lastRecord.liveStartTime > 0) {
            currentTime - lastRecord.liveStartTime
        } else 0L

        return LiveStats(
            totalLiveCount = totalLiveCount,
            totalLiveDuration = totalLiveDuration,
            historicalTitles = historicalTitles,
            recordStartTime = firstRecord.recordTime,
            monthlyLiveCount = monthlyLiveCount,
            monthlyLiveDuration = monthlyLiveDuration,
            monthlyTitles = monthlyTitles,
            isCurrentlyLive = isCurrentlyLive,
            currentLiveDuration = currentLiveDuration,
        )
    }

    /**
     * 计算累计开播时长（秒）
     * 将 LIVE_START 事件与紧随的 LIVE_STOP 事件配对，计算每次直播的时长
     */
    private fun calculateDuration(records: List<LiveRecord>, currentTime: Long): Long {
        var total = 0L
        var i = 0
        while (i < records.size) {
            val record = records[i]
            if (record.eventType == EventType.LIVE_START) {
                // 寻找紧随的 LIVE_STOP
                val stopRecord = records.subList(i + 1, records.size)
                    .firstOrNull { it.eventType == EventType.LIVE_STOP }
                if (stopRecord != null) {
                    // 从开播时间到下播记录时间
                    if (stopRecord.liveStartTime > 0) {
                        total += stopRecord.recordTime - stopRecord.liveStartTime
                    }
                    i = records.indexOf(stopRecord) + 1
                } else {
                    // 没有找到对应的下播记录，说明正在直播中
                    if (record.liveStartTime > 0) {
                        total += currentTime - record.liveStartTime
                    }
                    i++
                }
            } else {
                i++
            }
        }
        return total
    }

    /**
     * 计算当月开播时长（秒）
     */
    private fun calculateMonthlyDuration(records: List<LiveRecord>, month: String, currentTime: Long): Long {
        val monthRecords = records.filter { it.month == month }
        if (monthRecords.isEmpty()) return 0L

        // 获取当月开始和结束时间戳
        val yearMonth = YearMonth.parse(month)
        val monthStart = yearMonth.atDay(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond()
        val monthEnd = yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond()

        var total = 0L
        val allRecordsInRange = if (monthRecords.first().eventType == EventType.LIVE_STOP
            && monthRecords.first().liveStartTime > 0
            && monthRecords.first().liveStartTime < monthStart
        ) {
            // 第一个记录是下播，但开播时间在上个月——需要补算跨月部分
            total += monthRecords.first().recordTime - monthStart
            monthRecords
        } else {
            monthRecords
        }

        var i = 0
        while (i < allRecordsInRange.size) {
            val record = allRecordsInRange[i]
            if (record.eventType == EventType.LIVE_START) {
                val stopRecord = allRecordsInRange.subList(i + 1, allRecordsInRange.size)
                    .firstOrNull { it.eventType == EventType.LIVE_STOP }
                if (stopRecord != null) {
                    if (stopRecord.liveStartTime > 0) {
                        // 只计算在本月内的时长部分
                        val startInMonth = maxOf(record.recordTime, monthStart)
                        val endInMonth = minOf(stopRecord.recordTime, monthEnd)
                        if (endInMonth > startInMonth) {
                            total += endInMonth - startInMonth
                        }
                    }
                    i = allRecordsInRange.indexOf(stopRecord) + 1
                } else {
                    // 正在直播中，计算从本月开始或开播时间到当前时间
                    if (record.liveStartTime > 0) {
                        val startInMonth = maxOf(record.liveStartTime, monthStart)
                        val endInMonth = minOf(currentTime, monthEnd)
                        if (endInMonth > startInMonth) {
                            total += endInMonth - startInMonth
                        }
                    }
                    i++
                }
            } else {
                i++
            }
        }
        return total
    }

    /**
     * 获取所有被记录过的主播UID列表
     */
    fun getRecordedUids(): List<Long> = repository.findDistinctUids()
}
