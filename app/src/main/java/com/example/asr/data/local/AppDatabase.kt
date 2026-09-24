package com.example.asr.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.asr.data.local.dao.ChatDao
import com.example.asr.data.local.dao.ChildDao
import com.example.asr.data.local.dao.KidStarDao
import com.example.asr.data.local.dao.MasteryHistoryDao
import com.example.asr.data.local.dao.RecordingDao
import com.example.asr.data.local.dao.RecordingPhotoDao
import com.example.asr.data.local.dao.ReviewTaskDao
import com.example.asr.data.local.dao.TranscriptDao
import com.example.asr.data.local.dao.WeakPointDao
import com.example.asr.data.local.dao.WorkRecordingDao
import com.example.asr.data.local.dao.WorkTodoDao
import com.example.asr.data.local.entity.ChatMessageEntity
import com.example.asr.data.local.entity.ChatSessionEntity
import com.example.asr.data.local.entity.ChildEntity
import com.example.asr.data.local.entity.KidStarEntity
import com.example.asr.data.local.entity.MasteryHistoryEntity
import com.example.asr.data.local.entity.RecordingEntity
import com.example.asr.data.local.entity.RecordingPhotoEntity
import com.example.asr.data.local.entity.ReviewTaskEntity
import com.example.asr.data.local.entity.TranscriptSegmentEntity
import com.example.asr.data.local.entity.WeakPointEntity
import com.example.asr.data.local.entity.WorkRecordingEntity
import com.example.asr.data.local.entity.WorkTodoEntity

@Database(
    entities = [
        ChildEntity::class,
        RecordingEntity::class,
        TranscriptSegmentEntity::class,
        WeakPointEntity::class,
        ReviewTaskEntity::class,
        MasteryHistoryEntity::class,
        RecordingPhotoEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        KidStarEntity::class,
        WorkRecordingEntity::class,
        WorkTodoEntity::class,
    ],
    version = 9,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao
    abstract fun recordingDao(): RecordingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun weakPointDao(): WeakPointDao
    abstract fun reviewTaskDao(): ReviewTaskDao
    abstract fun masteryHistoryDao(): MasteryHistoryDao
    abstract fun recordingPhotoDao(): RecordingPhotoDao
    abstract fun chatDao(): ChatDao
    abstract fun kidStarDao(): KidStarDao
    abstract fun workRecordingDao(): WorkRecordingDao
    abstract fun workTodoDao(): WorkTodoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 → v2：recordings 表新增 polishedText 列 */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN polishedText TEXT")
            }
        }

        /** v2 → v3：recordings 表新增 transcribedAt / polishedAt 时间戳列 */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN transcribedAt INTEGER")
                db.execSQL("ALTER TABLE recordings ADD COLUMN polishedAt INTEGER")
            }
        }

        /** v3 → v4：review_tasks 表新增 content 列（练习内容缓存） */
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE review_tasks ADD COLUMN content TEXT")
            }
        }

        /** v4 → v5：新增 mastery_history 表（掌握度历史快照，成长曲线/周报用） */
        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS mastery_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        weakPointId INTEGER NOT NULL,
                        mastery INTEGER NOT NULL,
                        recordedAt INTEGER NOT NULL,
                        FOREIGN KEY(weakPointId) REFERENCES weak_points(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_mastery_history_weakPointId " +
                        "ON mastery_history(weakPointId)"
                )
            }
        }

        /** v5 → v6：新增 recording_photos 表（记录附带的错题照片） */
        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recording_photos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        recordingId INTEGER NOT NULL,
                        filePath TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(recordingId) REFERENCES recordings(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recording_photos_recordingId " +
                        "ON recording_photos(recordingId)"
                )
            }
        }

        /** v6 → v7：children 加 voiceId、weak_points 加 exerciseCache；新增辅导会话/星星/工作端录音四张表 */
        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE children ADD COLUMN voiceId TEXT")
                db.execSQL("ALTER TABLE weak_points ADD COLUMN exerciseCache TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        childId INTEGER NOT NULL,
                        mode TEXT NOT NULL,
                        refId INTEGER,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(childId) REFERENCES children(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_chat_sessions_childId " +
                        "ON chat_sessions(childId)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        role TEXT NOT NULL,
                        text TEXT NOT NULL,
                        imagePaths TEXT,
                        audioUrl TEXT,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES chat_sessions(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_chat_messages_sessionId " +
                        "ON chat_messages(sessionId)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS kid_stars (
                        childId INTEGER PRIMARY KEY NOT NULL,
                        stars INTEGER NOT NULL,
                        FOREIGN KEY(childId) REFERENCES children(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS work_recordings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        scenario TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        segments TEXT,
                        durationSec INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        transcriptText TEXT,
                        summary TEXT,
                        transcribedAt INTEGER,
                        analyzedAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS work_todos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workRecordingId INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        assignee TEXT,
                        deadline TEXT,
                        done INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(workRecordingId) REFERENCES work_recordings(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_work_todos_workRecordingId " +
                        "ON work_todos(workRecordingId)"
                )
            }
        }

        /** v7 → v8：recordings 表新增 segments 列（长录音分段文件 JSON 数组） */
        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN segments TEXT")
            }
        }

        /** v8 → v9：chat_sessions 加 systemPrompt（创建时定型的辅导上下文）、chat_messages 加 contextText（照片描述等） */
        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_sessions ADD COLUMN systemPrompt TEXT")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN contextText TEXT")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "asr_tutor.db",
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                    MIGRATION_8_9,
                ).build().also { INSTANCE = it }
            }
    }
}
