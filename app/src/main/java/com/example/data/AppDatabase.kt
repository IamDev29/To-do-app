package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Category::class, Task::class], version = 1, exportSchema = false)
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

                // Add simple introductory onboarding tasks
                // This makes the first-time use experience extremely complete
                val onboardTasks = listOf(
                    Task(
                        title = "Welcome to Dark Todo! ⚡",
                        description = "This is a minimalist dark space to record your tasks. Swipe or tap the checkbox to complete me.",
                        priority = 2, // High priority
                        isCompleted = false,
                        categoryId = 2 // Personal Focus
                    ),
                    Task(
                        title = "Create a custom folder",
                        description = "Tap the '+' icon on the sidebar or project layout to organize tasks into folders.",
                        priority = 1,
                        isCompleted = false,
                        categoryId = 4 // Creative
                    ),
                    Task(
                        title = "Explore detailed analytics 📊",
                        description = "Check out your custom productivity vectors under the Analytics tab! Complete some tasks to populate details.",
                        priority = 0,
                        isCompleted = true,
                        categoryId = 1 // Work & Tech
                    )
                )
                for (task in onboardTasks) {
                    taskDao.insertTask(task)
                }
            }
        }
    }
}
