package tech.lq0.record

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 直播状态变更事件类型
 */
object EventType {
    const val LIVE_START = "LIVE_START"
    const val LIVE_STOP = "LIVE_STOP"
    const val TITLE_CHANGE = "TITLE_CHANGE"
}

/**
 * 直播状态记录实体
 * 每次状态变更（开播、下播、标题更改）时记录一条
 */
@Entity
@Table(
    name = "live_records", indexes = [
        Index(name = "idx_uid_month", columnList = "uid, record_month"),
        Index(name = "idx_uid", columnList = "uid"),
    ]
)
data class LiveRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** 主播UID */
    @Column(name = "uid", nullable = false)
    val uid: Long,

    /** 直播间ID */
    @Column(name = "room_id", nullable = false)
    val roomId: Long,

    /** 事件类型: LIVE_START / LIVE_STOP / TITLE_CHANGE */
    @Column(name = "event_type", nullable = false, length = 32)
    val eventType: String,

    /** 直播间标题（变更后） */
    @Column(name = "title", length = 512)
    val title: String = "",

    /** 直播间标题（变更前），仅 TITLE_CHANGE 事件使用 */
    @Column(name = "previous_title", length = 512)
    val previousTitle: String = "",

    /** 开播时间戳（秒），由B站API返回；非开播事件则继承上次的值 */
    @Column(name = "live_start_time")
    val liveStartTime: Long = 0,

    /** 记录创建时间戳（秒） */
    @Column(name = "record_time", nullable = false)
    val recordTime: Long,

    /** 与上一条记录的时间差值（秒） */
    @Column(name = "time_diff")
    val timeDiff: Long = 0,

    /** 月份标识 YYYY-MM，方便按月查询 */
    @Column(name = "record_month", nullable = false, length = 7)
    val month: String,
) {
    /**
     * 将秒级时间戳格式化为可读字符串
     */
    fun formatTimestamp(epochSecond: Long): String {
        val instant = Instant.ofEpochSecond(epochSecond)
        val dateTime = LocalDateTime.ofInstant(instant, ZoneId.of("Asia/Shanghai"))
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }

    val recordTimeFormatted: String get() = formatTimestamp(recordTime)
    val liveStartTimeFormatted: String get() = if (liveStartTime > 0) formatTimestamp(liveStartTime) else "未知"

    companion object {
        /**
         * 根据秒级时间戳生成月份标识
         */
        fun monthOf(epochSecond: Long): String {
            val instant = Instant.ofEpochSecond(epochSecond)
            val dateTime = LocalDateTime.ofInstant(instant, ZoneId.of("Asia/Shanghai"))
            return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        }
    }
}
