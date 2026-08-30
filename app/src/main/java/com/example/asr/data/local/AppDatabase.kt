package com.example.asr.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.asr.data.local.dao.ChildDao
import com.example.asr.data.local.dao.MasteryHistoryDao
import com.example.asr.data.local.dao.RecordingDao
import com.example.asr.data.local.dao.RecordingPhotoDao
import com.example.asr.data.local.dao.ReviewTaskDao
import com.example.asr.data.local.dao.TranscriptDao
import com.example.asr.data.local.dao.WeakPointDao
import com.example.asr.data.local.entity.ChildEntity
import com.example.asr.data.local.entity.MasteryHistoryEntity
import com.example.asr.data.local.entity.RecordingEntity
import com.example.asr.data.local.entity.RecordingPhotoEntity
import com.example.asr.data.local.entity.ReviewTaskEntity
import com.example.asr.data.local.entity.TranscriptSegmentEntity
import com.example.asr.data.local.entity.WeakPointEntity

@Database(
    entities = [
        ChildEntity::class,
        RecordingEntity::class,
        TranscriptSegmentEntity::class,
        WeakPointEntity::class,
        ReviewTaskEntity::class,
        MasteryHistoryEntity::class,
        RecordingPhotoEntity::class,
    ],
    version = 6,
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

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "asr_tutor.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { INSTANCE = it }
            }
    }
}
