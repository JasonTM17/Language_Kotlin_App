package com.linguaai.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.linguaai.app.data.local.dao.AiMessageCacheDao
import com.linguaai.app.data.local.dao.GrammarDao
import com.linguaai.app.data.local.dao.LessonDao
import com.linguaai.app.data.local.dao.SyncDao
import com.linguaai.app.data.local.dao.VocabularyDao
import com.linguaai.app.data.local.entity.AiMessageCacheEntity
import com.linguaai.app.data.local.entity.GrammarEntity
import com.linguaai.app.data.local.entity.LessonEntity
import com.linguaai.app.data.local.entity.PendingSyncOpEntity
import com.linguaai.app.data.local.entity.VocabularyEntity

@Database(
    entities = [
        LessonEntity::class,
        VocabularyEntity::class,
        GrammarEntity::class,
        PendingSyncOpEntity::class,
        AiMessageCacheEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class LinguaDatabase : RoomDatabase() {
    abstract fun lessonDao(): LessonDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun grammarDao(): GrammarDao
    abstract fun syncDao(): SyncDao
    abstract fun aiMessageCacheDao(): AiMessageCacheDao

    companion object {
        const val NAME = "linguaai.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
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
    }
}
