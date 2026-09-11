package com.linguaai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.linguaai.app.data.local.entity.GrammarEntity
import com.linguaai.app.data.local.entity.LessonEntity
import com.linguaai.app.data.local.entity.ProgressCacheEntity
import com.linguaai.app.data.local.entity.VocabularyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(lessons: List<LessonEntity>)

    @Query("SELECT * FROM lessons WHERE (:languageId IS NULL OR languageId = :languageId) AND (:level IS NULL OR level = :level) ORDER BY id")
    fun observeLessons(languageId: Long?, level: String?): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun findById(id: Long): LessonEntity?

    @Query("SELECT COUNT(*) FROM lessons")
    suspend fun count(): Int
}

@Dao
interface VocabularyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(words: List<VocabularyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(word: VocabularyEntity)

    @Query(
        """SELECT * FROM vocabulary
           WHERE (:languageId IS NULL OR languageId = :languageId)
             AND (:level IS NULL OR level = :level)
             AND (:category IS NULL OR category = :category)
             AND (:query IS NULL OR word LIKE '%' || :query || '%'
                  OR reading LIKE '%' || :query || '%'
                  OR meaning LIKE '%' || :query || '%')
           ORDER BY id""",
    )
    fun observeVocabulary(languageId: Long?, level: String?, category: String?, query: String?): Flow<List<VocabularyEntity>>

    @Query("SELECT * FROM vocabulary WHERE id = :id")
    suspend fun findById(id: Long): VocabularyEntity?

    @Query("SELECT * FROM vocabulary WHERE (nextReviewAt IS NULL OR nextReviewAt <= :now) AND (masteryLevel < 5) ORDER BY nextReviewAt IS NOT NULL, id LIMIT :limit")
    suspend fun dueForReview(now: Long, limit: Int): List<VocabularyEntity>

    @Query("SELECT COUNT(*) FROM vocabulary WHERE (nextReviewAt IS NULL OR nextReviewAt <= :now) AND masteryLevel < 5")
    suspend fun dueCount(now: Long): Int

    @Query("SELECT COUNT(*) FROM vocabulary WHERE languageId = :languageId")
    suspend fun countForLanguage(languageId: Long): Int

    @Query("UPDATE vocabulary SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)
}

@Dao
interface GrammarDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<GrammarEntity>)

    @Query("SELECT * FROM grammar_lessons WHERE (:languageId IS NULL OR languageId = :languageId) AND (:level IS NULL OR level = :level) ORDER BY id")
    fun observeGrammar(languageId: Long?, level: String?): Flow<List<GrammarEntity>>

    @Query("SELECT * FROM grammar_lessons WHERE id = :id")
    suspend fun findById(id: Long): GrammarEntity?
}

@Dao
interface ProgressCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: ProgressCacheEntity)

    @Query("SELECT * FROM progress_cache WHERE id = :id")
    suspend fun get(id: Int = ProgressCacheEntity.SINGLETON_ID): ProgressCacheEntity?

    @Query("SELECT * FROM progress_cache WHERE id = :id")
    fun observeEntry(id: Int = ProgressCacheEntity.SINGLETON_ID): Flow<ProgressCacheEntity?>

    @Query("DELETE FROM progress_cache")
    suspend fun clear()
}
