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

/** 辅导会话模式（与小程序 ChatSession.mode 一致） */
object ChatMode {
    const val FREE = "free"            // 自由提问
    const val WEAKPOINT = "weakpoint"  // 围绕某个薄弱点
    const val EXERCISE = "exercise"    // 围绕某次练习
}

/** 辅导消息角色 */
object ChatRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
}

/** 工作端录音场景（与小程序 WorkScenario 一致） */
object WorkScenario {
    const val MEETING = "meeting"  // 会议
    const val TALK = "talk"        // 工作谈话
    const val CALL = "call"        // 通话
}

/** 工作端录音处理状态（与小程序 WorkStatus 一致） */
object WorkStatus {
    const val RECORDED = "RECORDED"
    const val TRANSCRIBING = "TRANSCRIBING"
    const val TRANSCRIBED = "TRANSCRIBED"
    const val ANALYZING = "ANALYZING"
    const val ANALYZED = "ANALYZED"
    const val FAILED = "FAILED"
}

@Entity(tableName = "children")
@Serializable
data class ChildEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val grade: String? = null,
    /** 孩子专属辅导音色（MiniMax 预置音色值或复刻 voiceId）；null = 跟随全局默认 */
    val voiceId: String? = null,
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
    /** 最近一次生成的练习内容（TaskContent JSON），退出后重进可继续看 */
    val exerciseCache: String? = null,
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

/** AI 辅导会话（对应小程序 chat_sessions） */
@Entity(
    tableName = "chat_sessions",
    foreignKeys = [ForeignKey(
        entity = ChildEntity::class,
        parentColumns = ["id"],
        childColumns = ["childId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("childId")],
)
@Serializable
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val mode: String = ChatMode.FREE,
    /** weakpoint 模式 = weakPointId，exercise 模式 = reviewTaskId，free 模式为 null */
    val refId: Long? = null,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** AI 辅导消息（对应小程序 ChatSession.messages） */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(
        entity = ChatSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
@Serializable
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val text: String,
    /** 孩子发来的照片本地路径（JSON 数组），仅 user 消息有 */
    val imagePaths: String? = null,
    /** assistant 消息的播报音频链接（MiniMax，24h 有效，过期需重新合成） */
    val audioUrl: String? = null,
    val createdAt: Long,
)

/** 孩子端激励星星数（对应小程序 kid_stars） */
@Entity(
    tableName = "kid_stars",
    foreignKeys = [ForeignKey(
        entity = ChildEntity::class,
        parentColumns = ["id"],
        childColumns = ["childId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
@Serializable
data class KidStarEntity(
    @PrimaryKey val childId: Long,
    val stars: Int,
)

/** 工作端录音：会议/工作谈话/通话（对应小程序 WorkRecording） */
@Entity(tableName = "work_recordings")
@Serializable
data class WorkRecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val scenario: String,
    val filePath: String,
    /** 长录音的分段文件（JSON 数组，>1 段时存在，首段与 filePath 相同） */
    val segments: String? = null,
    val durationSec: Int,
    val createdAt: Long,
    val status: String = WorkStatus.RECORDED,
    /** 带说话人与时间戳的转写全文（[mm:ss] 说话人1：...） */
    val transcriptText: String? = null,
    /** AI 生成的纪要/总结（markdown 纯文本） */
    val summary: String? = null,
    val transcribedAt: Long? = null,
    val analyzedAt: Long? = null,
)

/** 工作端待办（对应小程序 WorkTodo） */
@Entity(
    tableName = "work_todos",
    foreignKeys = [ForeignKey(
        entity = WorkRecordingEntity::class,
        parentColumns = ["id"],
        childColumns = ["workRecordingId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("workRecordingId")],
)
@Serializable
data class WorkTodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workRecordingId: Long,
    val text: String,
    val assignee: String? = null,
    val deadline: String? = null,
    val done: Boolean = false,
    val createdAt: Long,
)

/** 工作端待办列表联查结果 */
data class WorkTodoWithRecording(
    val id: Long,
    val workRecordingId: Long,
    val text: String,
    val assignee: String?,
    val deadline: String?,
    val done: Boolean,
    val createdAt: Long,
    val recordingTitle: String,
)
