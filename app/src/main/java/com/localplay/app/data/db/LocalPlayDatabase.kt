package com.localplay.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Single Room database for LocalPlay.
 *
 * ### Schema versioning
 * Bump [version] whenever you add/modify/remove a column or table, and supply
 * a matching [androidx.room.migration.Migration] in [build] below.
 *
 * ### Thread safety
 * The companion-object [instance] is initialised once via double-checked
 * locking. All database operations must run on a non-UI thread; Room's
 * suspend-function support ensures this automatically when called from
 * a coroutine.
 */
@Database(
    entities = [SongEntity::class],
    version = 1,
    exportSchema = false,   // set to true and configure the Room Gradle plugin when you need schema history
)
abstract class LocalPlayDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    companion object {

        @Volatile
        private var instance: LocalPlayDatabase? = null

        fun getInstance(context: Context): LocalPlayDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalPlayDatabase::class.java,
                    "localplay.db",
                )
                    // Keep this during development; replace with a real Migration before release.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
