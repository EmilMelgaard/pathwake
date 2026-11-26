package io.github.emilmelgaard.pathwake.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PathwakeDatabase::class.java)

    @Test
    fun version1To2_keepsAlarmsAndSilencesThemAfterTenMinutes() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO alarms (id, hour, minute, label, enabled, repeat_days, date_epoch_day, pauses,
                    skipped_epoch_day, difficulty, puzzle_count)
                VALUES (7, 6, 45, 'Work', 1, 31, NULL, '', NULL, 'HARD', 2)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query("SELECT label, puzzle_count, auto_silence_minutes FROM alarms WHERE id = 7").use { row ->
                row.moveToFirst()
                assertEquals("Work", row.getString(0))
                assertEquals(2, row.getInt(1))
                assertEquals(10, row.getInt(2))
            }
        }
    }

    @Test
    fun version2To3_givesAlarmsTheDefaultSoundWithVibration() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO alarms (id, hour, minute, label, enabled, repeat_days, date_epoch_day, pauses,
                    skipped_epoch_day, difficulty, puzzle_count, auto_silence_minutes)
                VALUES (3, 7, 0, '', 1, 0, NULL, '', NULL, 'EASY', 1, NULL)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(DB, 3, true).use { db ->
            db.query("SELECT sound, sound_title, vibrate, auto_silence_minutes FROM alarms WHERE id = 3").use { row ->
                row.moveToFirst()
                assertEquals("default", row.getString(0))
                assertTrue(row.isNull(1))
                assertEquals(1, row.getInt(2))
                assertTrue(row.isNull(3))
            }
        }
    }

    @Test
    fun version3To4_letsAlarmsUseTheAlarmVolume() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO alarms (id, hour, minute, label, enabled, repeat_days, date_epoch_day, pauses,
                    skipped_epoch_day, difficulty, puzzle_count, auto_silence_minutes, sound, sound_title, vibrate)
                VALUES (4, 8, 30, '', 1, 0, NULL, '', NULL, 'MEDIUM', 1, NULL, 'default', NULL, 1)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(DB, 4, true).use { db ->
            db.query("SELECT difficulty, volume FROM alarms WHERE id = 4").use { row ->
                row.moveToFirst()
                assertEquals("MEDIUM", row.getString(0))
                assertTrue(row.isNull(1))
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
