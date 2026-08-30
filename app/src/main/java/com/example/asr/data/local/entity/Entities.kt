package com.example.asr.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 录音处理状态 */
object RecordingStatus {
    const val RECORDED = "RECORDED"           // 已录
    const val TRANSCRIBING = "TRANSCRIBING"   // 转写中
    const val TRANSCRIBED = "TRANSCRIBED"     // 已转写
    const val ANALYZED = "ANALYZED"           // 已分析
    const val FAILED = "FAILED"               // 失败
}

/** 说话人角色 */
object SpeakerRole {
    const val UNKNOWN = "UNKNOWN"  // 未标注
    const val PARENT = "PARENT"    // 家长
    const val CHILD = "CHILD"      // 孩子
}

@Entity(tableName = "children")
@Serializable
data class ChildEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val grade: String? = null,
)

@Entity(
    tableName = "recordings",
    foreignKeys = [ForeignKey(
        entity = ChildEntity::class,
        parentColumns = ["id"],
        childColumns = ["childId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("childId")],
)
@Serializable
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val subject: String,
    val filePath: String,
    val durationSec: Int,
    val createdAt: Long,
    val status: String = RecordingStatus.RECORDED,
    val polishedText: String? = null,   // 润色后的完整文稿
    val transcribedAt: Long? = null,    // 转写完成时间
    val polishedAt: Long? = null,       // 润色完成时间
)

@Entity(
    tableName = "transcript_segments",
    foreignKeys = [ForeignKey(
        entity = RecordingEntity::class,
        parentColumns = ["id"],
        childColumns = ["recordingId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recordingId", "speakerLabel")],
)
@Serializable
data class TranscriptSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val speakerLabel: String,
    val role: String = SpeakerRole.UNKNOWN,
    val startSec: Float,
    val endSec: Float,
    val text: String,
)

@Entity(
    tableName = "weak_points",
    foreignKeys = [ForeignKey(
        entity = ChildEntity::class,
        parentColumns = ["id"],
        childColumns = ["childId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("childId")],
)
@Serializable
data class WeakPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val subject: String,
    val knowledgePoint: String,
    val description: String,
    val mastery: Int,          // 0-100
    val reviewStage: Int,      // 从 0 开始；>= 间隔数 表示已完成
    val nextReviewAt: Long,    // epoch millis；-1 表示已完成不再排期
    val createdAt: Long,
    val sourceRecordingId: Long? = null,
)

@Entity(
    tableName = "review_tasks",
    foreignKeys = [ForeignKey(
        entity = WeakPointEntity::class,
        parentColumns = ["id"],
        childColumns = ["weakPointId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("weakPointId")],
)
@Serializable
data class ReviewTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weakPointId: Long,
    val dueDate: Long,
    val completedAt: Long? = null,
    val content: String? = null,   // 生成的练习内容（JSON 原文，惰性生成）
)

/** 掌握度历史快照：建档 / 合并 / 每次复习反馈时写入，用于成长曲线和周报 */
@Entity(
    tableName = "mastery_history",
    foreignKeys = [ForeignKey(
        entity = WeakPointEntity::class,
        parentColumns = ["id"],
        childColumns = ["weakPointId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("weakPointId")],
)
@Serializable
data class MasteryHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weakPointId: Long,
    val mastery: Int,
    val recordedAt: Long,
)

/** 记录附带的错题照片：本地文件路径，分析时编码为 base64 发给多模态模型 */
@Entity(
    tableName = "recording_photos",
    foreignKeys = [ForeignKey(
        entity = RecordingEntity::class,
        parentColumns = ["id"],
        childColumns = ["recordingId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recordingId")],
)
@Serializable
data class RecordingPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val filePath: String,
    val createdAt: Long,
)

/** 今日任务页 / 通知使用的联查结果 */
data class ReviewTaskWithWeakPoint(
    val taskId: Long,
    val dueDate: Long,
    val completedAt: Long?,
    val content: String?,
    val weakPointId: Long,
    val childId: Long,
    val subject: String,
    val knowledgePoint: String,
    val description: String,
    val mastery: Int,
    val reviewStage: Int,
    val childName: String,
)

/** 记录列表联查结果 */
data class RecordingWithChild(
    val id: Long,
    val childId: Long,
    val subject: String,
    val filePath: String,
    val durationSec: Int,
    val createdAt: Long,
    val status: String,
    val childName: String,
)
