package tech.lq0.record

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface LiveRecordRepository : JpaRepository<LiveRecord, Long> {

    /** 查询某主播的所有记录，按时间升序 */
    fun findByUidOrderByRecordTimeAsc(uid: Long): List<LiveRecord>

    /** 查询某主播某月的所有记录 */
    fun findByUidAndMonthOrderByRecordTimeAsc(uid: Long, month: String): List<LiveRecord>

    /** 查询某主播最近一条记录 */
    fun findTopByUidOrderByRecordTimeDesc(uid: Long): LiveRecord?

    /** 查询某主播最早一条记录 */
    fun findTopByUidOrderByRecordTimeAsc(uid: Long): LiveRecord?

    /** 查询某主播某月的LIVE_START事件数量 */
    @Query("SELECT COUNT(r) FROM LiveRecord r WHERE r.uid = :uid AND r.month = :month AND r.eventType = 'LIVE_START'")
    fun countLiveStartByUidAndMonth(@Param("uid") uid: Long, @Param("month") month: String): Long

    /** 查询某主播所有LIVE_START事件数量 */
    @Query("SELECT COUNT(r) FROM LiveRecord r WHERE r.uid = :uid AND r.eventType = 'LIVE_START'")
    fun countLiveStartByUid(@Param("uid") uid: Long): Long

    /** 查询所有不重复的主播UID */
    @Query("SELECT DISTINCT r.uid FROM LiveRecord r")
    fun findDistinctUids(): List<Long>

    /** 查询某主播某月的所有LIVE_STOP记录（用于计算时长） */
    @Query("SELECT r FROM LiveRecord r WHERE r.uid = :uid AND r.month = :month AND r.eventType = 'LIVE_STOP' ORDER BY r.recordTime ASC")
    fun findLiveStopsByUidAndMonth(@Param("uid") uid: Long, @Param("month") month: String): List<LiveRecord>

    /** 查询某主播的所有LIVE_STOP记录 */
    @Query("SELECT r FROM LiveRecord r WHERE r.uid = :uid AND r.eventType = 'LIVE_STOP' ORDER BY r.recordTime ASC")
    fun findAllLiveStopsByUid(@Param("uid") uid: Long): List<LiveRecord>

    /** 查询某主播某月最近一条LIVE_START记录（可能跨月的开播） */
    @Query("SELECT r FROM LiveRecord r WHERE r.uid = :uid AND r.eventType = 'LIVE_START' AND r.recordTime < :endOfMonth ORDER BY r.recordTime DESC")
    fun findLastLiveStartBeforeEndOfMonth(@Param("uid") uid: Long, @Param("endOfMonth") endOfMonth: Long): LiveRecord?

    /** 查询某主播某月所有记录中的直播间标题（去重） */
    @Query("SELECT DISTINCT r.title FROM LiveRecord r WHERE r.uid = :uid AND r.month = :month AND r.title <> ''")
    fun findDistinctTitlesByUidAndMonth(@Param("uid") uid: Long, @Param("month") month: String): List<String>

    /** 查询某主播所有记录中的直播间标题（去重） */
    @Query("SELECT DISTINCT r.title FROM LiveRecord r WHERE r.uid = :uid AND r.title <> ''")
    fun findDistinctTitlesByUid(@Param("uid") uid: Long): List<String>
}
