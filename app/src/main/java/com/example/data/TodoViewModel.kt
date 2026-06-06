package com.example.data

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.receiver.ReminderReceiver
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class TodoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TaskRepository
    val allTasks: StateFlow<List<Task>>
    val allCategories: StateFlow<List<Category>>

    // Filters & Selections
    val selectedCategoryFilter = MutableStateFlow<Int?>(null) // null = Show All
    val searchQuery = MutableStateFlow("")
    val priorityFilter = MutableStateFlow<Int?>(null) // null = Show All

    // Computed Stats
    private val _stats = MutableStateFlow(TodoStats())
    val stats: StateFlow<TodoStats> = _stats.asStateFlow()

    // Screen State / Sheet State for edit/create
    val currentVoiceText = MutableStateFlow<String?>(null)

    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = TaskRepository(database.taskDao())

        allTasks = repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allCategories = repository.allCategories.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Observe tasks to compute real-time statistics
        viewModelScope.launch {
            allTasks.collect { tasks ->
                computeStats(tasks)
            }
        }
    }

    private fun computeStats(tasks: List<Task>) {
        if (tasks.isEmpty()) {
            _stats.value = TodoStats()
            return
        }

        val total = tasks.size
        val completed = tasks.count { it.isCompleted }
        val pending = total - completed
        val completionRate = if (total > 0) (completed.toFloat() / total * 100).toInt() else 0

        val lowPriority = tasks.count { it.priority == 0 }
        val medPriority = tasks.count { it.priority == 1 }
        val highPriority = tasks.count { it.priority == 2 }

        // Category breakdown
        val categoryCounts = mutableMapOf<Int, Int>()
        val categoryCompleted = mutableMapOf<Int, Int>()

        for (task in tasks) {
            val catId = task.categoryId ?: -1
            categoryCounts[catId] = (categoryCounts[catId] ?: 0) + 1
            if (task.isCompleted) {
                categoryCompleted[catId] = (categoryCompleted[catId] ?: 0) + 1
            }
        }

        _stats.value = TodoStats(
            totalTasks = total,
            completedTasks = completed,
            pendingTasks = pending,
            completionRatePercentage = completionRate,
            lowPriorityCount = lowPriority,
            mediumPriorityCount = medPriority,
            highPriorityCount = highPriority,
            categoryTaskCounts = categoryCounts,
            categoryCompletedCounts = categoryCompleted
        )
    }

    // Task Actions
    fun addTask(
        title: String,
        description: String,
        dueDate: Long?,
        priority: Int,
        categoryId: Int?,
        isRecurring: Boolean,
        recurrencePattern: String?,
        reminderTime: Long?
    ) {
        viewModelScope.launch {
            val task = Task(
                title = title,
                description = description,
                dueDate = dueDate,
                priority = priority,
                isCompleted = false,
                categoryId = categoryId,
                isRecurring = isRecurring,
                recurrencePattern = recurrencePattern,
                reminderTime = reminderTime
            )
            val taskId = repository.insertTask(task)
            
            // Schedule reminder if set
            if (reminderTime != null && reminderTime > System.currentTimeMillis()) {
                scheduleNotification(getApplication(), taskId.toInt(), title, description, reminderTime)
            }
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun completeTaskToggle(task: Task) {
        viewModelScope.launch {
            if (!task.isCompleted && task.isRecurring && task.recurrencePattern != null) {
                // If checking an active recurring task, update it to the next cycle and reset completes
                val nextDueDate = calculateNextRecurrenceDate(task.dueDate ?: System.currentTimeMillis(), task.recurrencePattern)
                val updatedTask = task.copy(
                    isCompleted = false,
                    dueDate = nextDueDate,
                    reminderTime = task.reminderTime?.let { calculateNextRecurrenceDate(it, task.recurrencePattern) }
                )
                repository.updateTask(updatedTask)

                // Also insert a static log item of the completed iteration so the user sees progress
                val historicalCompletedTask = task.copy(
                    id = 0, // autoGenerate new ID
                    isCompleted = true,
                    isRecurring = false,
                    recurrencePattern = null
                )
                repository.insertTask(historicalCompletedTask)
            } else {
                // Regular simple task complete toggle
                val updatedTask = task.copy(isCompleted = !task.isCompleted)
                repository.updateTask(updatedTask)
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            cancelNotification(getApplication(), task.id)
            repository.deleteTask(task)
        }
    }

    // Category Actions
    fun addCategory(name: String, color: Int) {
        viewModelScope.launch {
            repository.insertCategory(Category(name = name, color = color))
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    // Utility: Date recurrence math
    private fun calculateNextRecurrenceDate(currentDate: Long, pattern: String): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = currentDate }
        when (pattern.uppercase()) {
            "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            "MONTHLY" -> cal.add(Calendar.MONTH, 1)
            else -> cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    // Alarm/Reminder integrations
    private fun scheduleNotification(context: Context, id: Int, title: String, content: String, timeInMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("task_id", id)
            putExtra("task_title", title)
            putExtra("task_desc", content)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            }
        } catch (e: Exception) {
            // Fallback for security restrictions on exact alarms
            alarmManager.set(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
        }
    }

    private fun cancelNotification(context: Context, id: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    // Calendar Provider Integration (LAUNCHES DEVICE CALENDAR ACTIVITY INTENT)
    fun linkTaskToDeviceCalendar(context: Context, task: Task) {
        val calendarTime = task.dueDate ?: System.currentTimeMillis()
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, task.title)
            putExtra(CalendarContract.Events.DESCRIPTION, task.description + "\n\n(Scheduled via Dark Todo app)")
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, calendarTime)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, calendarTime + 60 * 60 * 1000) // 1 hour duration
            putExtra(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
        }
        context.startActivity(intent)
    }

    // Backup Data Export to text share
    fun exportBackup(context: Context) {
        viewModelScope.launch {
            val tasksList = allTasks.value
            val categoriesList = allCategories.value

            val exportObject = JSONObject()
            
            val categoriesArray = JSONArray()
            for (cat in categoriesList) {
                val catObj = JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("color", cat.color)
                }
                categoriesArray.put(catObj)
            }
            exportObject.put("categories", categoriesArray)

            val tasksArray = JSONArray()
            for (t in tasksList) {
                val taskObj = JSONObject().apply {
                    put("title", t.title)
                    put("description", t.description)
                    put("dueDate", t.dueDate ?: -1L)
                    put("priority", t.priority)
                    put("isCompleted", t.isCompleted)
                    put("categoryId", t.categoryId ?: -1)
                    put("isRecurring", t.isRecurring)
                    put("recurrencePattern", t.recurrencePattern ?: "")
                    put("createdAt", t.createdAt)
                }
                tasksArray.put(taskObj)
            }
            exportObject.put("tasks", tasksArray)

            val jsonString = exportObject.toString(4)

            // Launch beautiful native Android Share Intent for backup contents
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "Dark_Todo_Backup_${System.currentTimeMillis()}.json")
                putExtra(Intent.EXTRA_TEXT, jsonString)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Backup Export via:"))
        }
    }
}

// Simple Holder for computed task lists and analytics summary statistics
data class TodoStats(
    val totalTasks: Int = 0,
    val completedTasks: Int = 0,
    val pendingTasks: Int = 0,
    val completionRatePercentage: Int = 0,
    val lowPriorityCount: Int = 0,
    val mediumPriorityCount: Int = 0,
    val highPriorityCount: Int = 0,
    val categoryTaskCounts: Map<Int, Int> = emptyMap(),
    val categoryCompletedCounts: Map<Int, Int> = emptyMap()
)

// ViewModel Factory
class TodoViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TodoViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
