package com.example.asr.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.asr.data.local.entity.ChildEntity
import com.example.asr.data.local.entity.MasteryHistoryEntity
import com.example.asr.data.local.entity.RecordingEntity
import com.example.asr.data.local.entity.RecordingPhotoEntity
import com.example.asr.data.local.entity.RecordingWithChild
import com.example.asr.data.local.entity.ReviewTaskEntity
import com.example.asr.data.local.entity.ReviewTaskWithWeakPoint
import com.example.asr.data.local.entity.TranscriptSegmentEntity
import com.example.asr.data.local.entity.WeakPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildDao {
    @Insert
    suspend fun insert(child: ChildEntity): Long

    @Update
    suspend fun update(child: ChildEntity)

    @Delete
    suspend fun delete(child: ChildEntity)

    @Query("SELECT * FROM children ORDER BY id ASC")
    fun observeAll(): Flow<List<ChildEntity>>

    @Query("SELECT * FROM children WHERE id = :id")
    suspend fun getById(id: Long): ChildEntity?

    /** 云备份/恢复用：全量读取 / 云端覆盖本地同 id 数据 */
    @Query("SELECT * FROM children")
    suspend fun getAll(): List<ChildEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChildEntity>)
}

@Dao
interface RecordingDao {
    @Insert
    suspend fun insert(recording: RecordingEntity): Long

    @Query("UPDATE recordings SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE recordings SET polishedText = :text WHERE id = :id")
    suspend fun updatePolishedText(id: Long, text: String)

    @Query("UPDATE recordings SET transcribedAt = :at WHERE id = :id")
    suspend fun updateTranscribedAt(id: Long, at: Long)

    @Query("UPDATE recordings SET polishedAt = :at WHERE id = :id")
    suspend fun updatePolishedAt(id: Long, at: Long)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Query(
        """
        SELECT r.id, r.childId, r.subject, r.filePath, r.durationSec, r.createdAt, r.status,
               c.name AS childName
        FROM recordings r JOIN children c ON c.id = r.childId
        WHERE (:childId IS NULL OR r.childId = :childId)
          AND (:subject IS NULL OR r.subject = :subject)
        ORDER BY r.createdAt DESC
        """
    )
    fun observeWithChild(childId: Long?, subject: String?): Flow<List<RecordingWithChild>>

    @Query("SELECT DISTINCT subject FROM recordings ORDER BY subject")
    fun observeSubjects(): Flow<List<String>>

    /** 云备份/恢复用 */
    @Query("SELECT * FROM recordings")
    suspend fun getAll(): List<RecordingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RecordingEntity>)
}

@Dao
interface TranscriptDao {
    @Insert
    suspend fun insertAll(segments: List<TranscriptSegmentEntity>)

    @Query("SELECT * FROM transcript_segments WHERE recordingId = :recordingId ORDER BY startSec ASC, id ASC")
    fun observeByRecording(recordingId: Long): Flow<List<TranscriptSegmentEntity>>

    @Query("SELECT * FROM transcript_segments WHERE recordingId = :recordingId ORDER BY startSec ASC, id ASC")
    suspend fun getByRecording(recordingId: Long): List<TranscriptSegmentEntity>

    @Query("DELETE FROM transcript_segments WHERE recordingId = :recordingId")
    suspend fun deleteByRecording(recordingId: Long)

    @Query("UPDATE transcript_segments SET role = :role WHERE recordingId = :recordingId AND speakerLabel = :speakerLabel")
    suspend fun updateRoleForSpeaker(recordingId: Long, speakerLabel: String, role: String)

    /** 云备份/恢复用 */
    @Query("SELECT * FROM transcript_segments")
    suspend fun getAll(): List<TranscriptSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TranscriptSegmentEntity>)
}

@Dao
interface WeakPointDao {
    @Insert
    suspend fun insert(weakPoint: WeakPointEntity): Long

    @Update
    suspend fun update(weakPoint: WeakPointEntity)

    @Query("DELETE FROM weak_points WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM weak_points WHERE id = :id")
    suspend fun getById(id: Long): WeakPointEntity?

    @Query(
        """
        SELECT * FROM weak_points
        WHERE (:childId IS NULL OR childId = :childId)
          AND (:subject IS NULL OR subject = :subject)
        ORDER BY nextReviewAt = -1, nextReviewAt ASC
        """
    )
    fun observe(childId: Long?, subject: String?): Flow<List<WeakPointEntity>>

    @Query("SELECT * FROM weak_points WHERE sourceRecordingId = :recordingId ORDER BY id ASC")
    fun observeByRecording(recordingId: Long): Flow<List<WeakPointEntity>>

    /** 查重用：取某孩子某科目的全部已有薄弱点 */
    @Query("SELECT * FROM weak_points WHERE childId = :childId AND subject = :subject ORDER BY id ASC")
    suspend fun getByChildAndSubject(childId: Long, subject: String): List<WeakPointEntity>

    @Query("SELECT DISTINCT subject FROM weak_points ORDER BY subject")
    fun observeSubjects(): Flow<List<String>>

    /** 云备份/恢复用 */
    @Query("SELECT * FROM weak_points")
    suspend fun getAll(): List<WeakPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WeakPointEntity>)
}

@Dao
interface ReviewTaskDao {
    @Insert
    suspend fun insert(task: ReviewTaskEntity): Long

    @Query("UPDATE review_tasks SET completedAt = :completedAt WHERE id = :taskId")
    suspend fun complete(taskId: Long, completedAt: Long)

    @Query("SELECT * FROM review_tasks WHERE id = :taskId")
    suspend fun getById(taskId: Long): ReviewTaskEntity?

    @Query("UPDATE review_tasks SET content = :content WHERE id = :taskId")
    suspend fun updateContent(taskId: Long, content: String)

    @Query("DELETE FROM review_tasks WHERE weakPointId = :weakPointId")
    suspend fun deleteByWeakPoint(weakPointId: Long)

    @Query(
        """
        SELECT t.id AS taskId, t.dueDate, t.completedAt, t.content,
               w.id AS weakPointId, w.childId, w.subject, w.knowledgePoint,
               w.description, w.mastery, w.reviewStage,
               c.name AS childName
        FROM review_tasks t
        JOIN weak_points w ON w.id = t.weakPointId
        JOIN children c ON c.id = w.childId
        WHERE t.completedAt IS NULL AND t.dueDate <= :todayEnd AND w.nextReviewAt != -1
        ORDER BY t.dueDate ASC, w.nextReviewAt ASC
        """
    )
    fun observeDue(todayEnd: Long): Flow<List<ReviewTaskWithWeakPoint>>

    @Query(
        """
        SELECT COUNT(*) FROM review_tasks t
        JOIN weak_points w ON w.id = t.weakPointId
        WHERE t.completedAt IS NULL AND t.dueDate <= :todayEnd AND w.nextReviewAt != -1
        """
    )
    suspend fun countDue(todayEnd: Long): Int

    /** 云备份/恢复用 */
    @Query("SELECT * FROM review_tasks")
    suspend fun getAll(): List<ReviewTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ReviewTaskEntity>)

    /** 周报用：某时刻之后完成的复习任务，按科目分组计数 */
    @Query(
        """
        SELECT w.subject AS subject, COUNT(*) AS count
        FROM review_tasks t JOIN weak_points w ON w.id = t.weakPointId
        WHERE t.completedAt IS NOT NULL AND t.completedAt >= :since
        GROUP BY w.subject ORDER BY count DESC
        """
    )
    suspend fun countCompletedBySubject(since: Long): List<SubjectCount>
}

/** 周报科目分布行 */
data class SubjectCount(val subject: String, val count: Int)

/** 周报掌握度快照行（联查薄弱点 + 孩子） */
data class MasteryHistoryRow(
    val weakPointId: Long,
    val mastery: Int,
    val recordedAt: Long,
    val knowledgePoint: String,
    val subject: String,
    val childName: String,
)

@Dao
interface MasteryHistoryDao {
    @Insert
    suspend fun insert(item: MasteryHistoryEntity): Long

    @Query("SELECT * FROM mastery_history WHERE weakPointId = :weakPointId ORDER BY recordedAt ASC, id ASC")
    fun observeByWeakPoint(weakPointId: Long): Flow<List<MasteryHistoryEntity>>

    /** 周报用：某时刻之后记录的全部快照 */
    @Query(
        """
        SELECT h.weakPointId, h.mastery, h.recordedAt,
               w.knowledgePoint, w.subject, c.name AS childName
        FROM mastery_history h
        JOIN weak_points w ON w.id = h.weakPointId
        JOIN children c ON c.id = w.childId
        WHERE h.recordedAt >= :since
        ORDER BY h.recordedAt ASC, h.id ASC
        """
    )
    suspend fun getSince(since: Long): List<MasteryHistoryRow>

    /** 云备份/恢复用 */
    @Query("SELECT * FROM mastery_history")
    suspend fun getAll(): List<MasteryHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MasteryHistoryEntity>)
}

@Dao
interface RecordingPhotoDao {
    @Insert
    suspend fun insertAll(items: List<RecordingPhotoEntity>)

    @Query("SELECT * FROM recording_photos WHERE recordingId = :recordingId ORDER BY id ASC")
    fun observeByRecording(recordingId: Long): Flow<List<RecordingPhotoEntity>>

    @Query("SELECT * FROM recording_photos WHERE recordingId = :recordingId ORDER BY id ASC")
    suspend fun getByRecording(recordingId: Long): List<RecordingPhotoEntity>

    @Query("DELETE FROM recording_photos WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 云备份/恢复用 */
    @Query("SELECT * FROM recording_photos")
    suspend fun getAll(): List<RecordingPhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RecordingPhotoEntity>)
}
