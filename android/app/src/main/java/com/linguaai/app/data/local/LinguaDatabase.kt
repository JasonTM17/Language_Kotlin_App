package com.linguaai.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.linguaai.app.data.local.dao.AiMessageCacheDao
import com.linguaai.app.data.local.dao.GrammarDao
import com.linguaai.app.data.local.dao.LessonDao
import com.linguaai.app.data.local.dao.ProgressCacheDao
import com.linguaai.app.data.local.dao.SyncDao
import com.linguaai.app.data.local.dao.VocabularyDao
import com.linguaai.app.data.local.entity.AiMessageCacheEntity
import com.linguaai.app.data.local.entity.GrammarEntity
import com.linguaai.app.data.local.entity.LessonEntity
import com.linguaai.app.data.local.entity.PendingSyncOpEntity
import com.linguaai.app.data.local.entity.ProgressCacheEntity
import com.linguaai.app.data.local.entity.VocabularyEntity

@Database(
    entities = [
        LessonEntity::class,
        VocabularyEntity::class,
        GrammarEntity::class,
        PendingSyncOpEntity::class,
        AiMessageCacheEntity::class,
        ProgressCacheEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class LinguaDatabase : RoomDatabase() {
    abstract fun lessonDao(): LessonDao

    abstract fun vocabularyDao(): VocabularyDao

    abstract fun grammarDao(): GrammarDao

    abstract fun syncDao(): SyncDao

    abstract fun aiMessageCacheDao(): AiMessageCacheDao

    abstract fun progressCacheDao(): ProgressCacheDao

    companion object {
        const val NAME = "linguaai.db"

        // The schema versions the migrations below step between. Naming them
        // keeps each `Migration(from, to)` pair readable and stops a version
        // number being retyped from the @Database annotation.
        private const val SCHEMA_V1 = 1
        private const val SCHEMA_V2 = 2
        private const val SCHEMA_V3 = 3
        private const val SCHEMA_V4 = 4
        private const val SCHEMA_V5 = 5
        private const val SCHEMA_V6 = 6
        private const val SCHEMA_V7 = 7

        val MIGRATION_1_2 =
            object : Migration(SCHEMA_V1, SCHEMA_V2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS ai_message_cache (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "conversationId INTEGER NOT NULL, " +
                            "role TEXT NOT NULL, " +
                            "content TEXT NOT NULL, " +
                            "cachedAt INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_ai_message_cache_conversationId ON ai_message_cache(conversationId)",
                    )
                }
            }

        val MIGRATION_2_3 =
            object : Migration(SCHEMA_V2, SCHEMA_V3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // DDL kept byte-identical to the Room-exported schema
                    // (app/schemas/.../3.json) so Room's migration validation is an
                    // exact match rather than relying on PRAGMA equivalence.
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `progress_cache` (" +
                            "`id` INTEGER NOT NULL, " +
                            "`payload` TEXT NOT NULL, " +
                            "`cachedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))",
                    )
                }
            }

        val MIGRATION_3_4 =
            object : Migration(SCHEMA_V3, SCHEMA_V4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Adds the retry counter to the outbox so a permanently failing
                    // operation can be dead-lettered instead of retried forever.
                    // The DEFAULT matches @ColumnInfo(defaultValue = "0") on the entity.
                    db.execSQL(
                        "ALTER TABLE `pending_sync_ops` ADD COLUMN `attempts` INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        val MIGRATION_4_5 =
            object : Migration(SCHEMA_V4, SCHEMA_V5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Nullable columns preserve pre-existing generic events while
                    // allowing flashcard reviews to carry their SRS snapshot.
                    db.execSQL("ALTER TABLE `pending_sync_ops` ADD COLUMN `vocabularyFavorite` INTEGER")
                    db.execSQL("ALTER TABLE `pending_sync_ops` ADD COLUMN `vocabularyMasteryLevel` INTEGER")
                    db.execSQL("ALTER TABLE `pending_sync_ops` ADD COLUMN `vocabularyReviewCount` INTEGER")
                    db.execSQL("ALTER TABLE `pending_sync_ops` ADD COLUMN `vocabularyCorrectCount` INTEGER")
                    db.execSQL("ALTER TABLE `pending_sync_ops` ADD COLUMN `vocabularyWrongCount` INTEGER")
                    db.execSQL("ALTER TABLE `pending_sync_ops` ADD COLUMN `vocabularyLastReviewedAt` INTEGER")
                    db.execSQL("ALTER TABLE `pending_sync_ops` ADD COLUMN `vocabularyNextReviewAt` INTEGER")
                }
            }

        val MIGRATION_6_7 =
            object : Migration(SCHEMA_V6, SCHEMA_V7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Nullable: cached turns written before this carry no
                    // citations, which is the same shape as a reply from a mode
                    // that does not retrieve.
                    db.execSQL("ALTER TABLE `ai_message_cache` ADD COLUMN `sourcesJson` TEXT")
                }
            }

        val MIGRATION_5_6 =
            object : Migration(SCHEMA_V5, SCHEMA_V6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Nullable keeps existing outbox events deliverable while
                    // newer state-sync events gain a logical client version.
                    db.execSQL("ALTER TABLE `pending_sync_ops` ADD COLUMN `vocabularyStateUpdatedAt` INTEGER")
                    db.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `stateUpdatedAt` INTEGER")
                }
            }
    }
}
