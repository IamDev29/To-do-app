package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Category::class, Task::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dark_todo_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateDatabase(database.taskDao())
                    }
                }
            }

            suspend fun populateDatabase(taskDao: TaskDao) {
                // Precompile default categorized project folders with beautiful dark theme colors
                val defaultFolders = listOf(
                    Category(name = "Work & Tech", color = 0xFF00E5FF.toInt()), // Cyan accent
                    Category(name = "Personal Focus", color = 0xFFFFAB40.toInt()), // Orange / Amber accent
                    Category(name = "Wellness & Gym", color = 0xFF64FFDA.toInt()), // Teal accent
                    Category(name = "Creative Flow", color = 0xFFE040FB.toInt())  // Magenta/Purple accent
                )
                for (folder in defaultFolders) {
                    taskDao.insertCategory(folder)
                }

                // Demo tasks have been entirely removed as the app is ready for deployment to start from scratch.
            }
        }
    }
}
