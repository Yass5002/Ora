/*
 *
 * ██████╗  █████╗ ████████╗ █████╗ ██████╗  █████╗ ███████╗███████╗
 * ██╔══██╗██╔══██╗╚══██╔══╝██╔══██╗██╔══██╗██╔══██╗██╔════╝██╔════╝
 * ██║  ██║███████║   ██║   ███████║██████╔╝███████║███████╗█████╗
 * ██║  ██║██╔══██║   ██║   ██╔══██║██╔══██╗██╔══██║╚════██║██╔══╝
 * ██████╔╝██║  ██║   ██║   ██║  ██║██████╔═══╝ ╚═╝  ╚═╝   ╚═╝   ╚═╝  ╚═╝╚═════╝ ╚═╝  ╚═╝╚══════╝╚══════╝
 *
 * CountdownDatabase.kt - The Fortress of Data 🏰
 * Where all your precious countdowns live, safely stored and organized.
 * Like a digital vault that never forgets your important moments!
 *╝██║  ██║███████║███████╗
 * ╚══
 * Database magic crafted by Yassine 🧙‍♂️
 * "In Room we trust!" - Because SQLite is so 2010 😉
 */

package com.dev.ora.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context

@Database(
    entities = [CountdownEvent::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CountdownDatabase : RoomDatabase() {

    abstract fun countdownEventDao(): CountdownEventDao

    companion object {
        @Volatile
        private var INSTANCE: CountdownDatabase? = null

        fun getDatabase(context: Context): CountdownDatabase {
            // ╭──────────────────────────────────╮
            // │ 🏗️ Database Builder - Yassine 🏗️ │
            // ╰──────────────────────────────────╯
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CountdownDatabase::class.java,
                    "countdown_database"
                )
                .addMigrations(MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add reminder fields to existing table
                database.execSQL("ALTER TABLE countdown_events ADD COLUMN hasReminders INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE countdown_events ADD COLUMN reminderIntervals TEXT DEFAULT NULL")
            }
        }
    }
}
