package com.linguaai.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real Room migrations against a real database.
 *
 * This exists because every migration in this project so far has only ever been
 * checked by comparing its DDL against the exported schema — never executed. A
 * schema diff cannot catch a migration that throws, loses a column, or leaves
 * the database in a state Room refuses to open. Only running it can.
 *
 * Requires a device or emulator (`connectedDebugAndroidTest`), so it does not
 * run in a plain JVM build.
 */
@RunWith(AndroidJUnit4::class)
class LinguaDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            LinguaDatabase::class.java,
        )

    @Test
    fun migrate1To2_addsAiMessageCache() {
        helper.createDatabase(TEST_DB, 1).close()

        val db =
            helper.runMigrationsAndValidate(
                TEST_DB,
                2,
                true,
                LinguaDatabase.MIGRATION_1_2,
            )

        // The table must exist and be usable, not merely present in the schema.
        db.query("SELECT COUNT(*) FROM ai_message_cache").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate2To3_addsProgressCache() {
        helper.createDatabase(TEST_DB, 2).close()

        val db =
            helper.runMigrationsAndValidate(
                TEST_DB,
                3,
                true,
                LinguaDatabase.MIGRATION_2_3,
            )

        db.query("SELECT COUNT(*) FROM progress_cache").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate3To4_addsOutboxAttemptsWithDefault() {
        helper.createDatabase(TEST_DB, 3).close()

        val db =
            helper.runMigrationsAndValidate(
                TEST_DB,
                4,
                true,
                LinguaDatabase.MIGRATION_3_4,
            )

        // The whole point of the column is that pre-existing outbox rows get a
        // sane retry count rather than null, so assert the default was applied.
        db.execSQL(
            "INSERT INTO pending_sync_ops " +
                "(operationId, eventType, refId, minutes, occurredAt, state) " +
                "VALUES ('op-migration-test', 'QUIZ_ATTEMPT', NULL, 5, 0, 'PENDING')",
        )
        db.query("SELECT attempts FROM pending_sync_ops").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate1To4_runsTheWholeChain() {
        helper.createDatabase(TEST_DB, 1).close()

        val db =
            helper.runMigrationsAndValidate(
                TEST_DB,
                4,
                true,
                LinguaDatabase.MIGRATION_1_2,
                LinguaDatabase.MIGRATION_2_3,
                LinguaDatabase.MIGRATION_3_4,
            )

        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
