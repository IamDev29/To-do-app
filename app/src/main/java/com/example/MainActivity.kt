package com.example

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.DatePicker
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: TodoViewModel

    // Speech recognition launcher
    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val spokenTextList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = spokenTextList?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.currentVoiceText.value = spokenText
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val factory = TodoViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[TodoViewModel::class.java]

        // Handle Quick Add Intent from Widget
        val isQuickAddIntent = intent?.getBooleanExtra("is_quick_add", false) ?: false
        val actionQuickAdd = intent?.action == "com.example.action.QUICK_ADD"
        
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TodoMainDashboard(
                        viewModel = viewModel,
                        triggerVoiceInput = { launchSpeechInput() },
                        initialOpenQuickAdd = isQuickAddIntent || actionQuickAdd
                    )
                }
            }
        }
    }

    private fun launchSpeechInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "⚡ Dictate your dark to-do...")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Speech services currently unavailable", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoMainDashboard(
    viewModel: TodoViewModel,
    triggerVoiceInput: () -> Unit,
    initialOpenQuickAdd: Boolean
) {
    val context = LocalContext.current
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val priorityFilter by viewModel.priorityFilter.collectAsStateWithLifecycle()

    val voiceTextState by viewModel.currentVoiceText.collectAsStateWithLifecycle()

    // Dialog sheets state
    var showAddTaskSheet by remember { mutableStateOf(initialOpenQuickAdd) }
    var showAnalyticsDialog by remember { mutableStateOf(false) }
    var showCalendarDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    // Prepopulate quick voice inputs if available
    LaunchedEffect(voiceTextState) {
        if (voiceTextState != null) {
            showAddTaskSheet = true
        }
    }

    // Filtered lists
    val filteredTasks = remember(tasks, selectedCategoryFilter, searchQuery, priorityFilter) {
        tasks.filter { t ->
            val matchesCategory = selectedCategoryFilter == null || t.categoryId == selectedCategoryFilter
            val matchesPriority = priorityFilter == null || t.priority == priorityFilter
            val matchesSearch = searchQuery.isBlank() || 
                    t.title.contains(searchQuery, ignoreCase = true) ||
                    t.description.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesPriority && matchesSearch
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Color(0xFF0F0F0F))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2D1F3D).copy(alpha = 0.40f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = size.width * 1.5f
                    ),
                    center = Offset(0f, 0f),
                    radius = size.width * 1.5f
                )
            }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val currentDateStr = remember {
                                SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
                            }
                            Text(
                                currentDateStr.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.6.sp,
                                    color = NeonCyan
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "My Tasks",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Light,
                                    letterSpacing = 0.4.sp
                                ),
                                color = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showAnalyticsDialog = true },
                            modifier = Modifier.testTag("action_analytics")
                        ) {
                            Icon(Icons.Default.Analytics, contentDescription = "Analytics", tint = NeonAmber)
                        }
                        IconButton(
                            onClick = { showCalendarDialog = true },
                            modifier = Modifier.testTag("action_calendar")
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = "Calendar view", tint = NeonCyan)
                        }
                        IconButton(
                            onClick = { viewModel.exportBackup(context) },
                            modifier = Modifier.testTag("action_export")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Backup export", tint = NeonTeal)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            floatingActionButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voice Dictation FAB
                    FloatingActionButton(
                        onClick = { triggerVoiceInput() },
                        containerColor = PremiumGlassBg,
                        contentColor = NeonCyan,
                        shape = CircleShape,
                        modifier = Modifier
                            .testTag("fab_voice")
                            .border(BorderStroke(1.dp, PremiumGlassBorder), CircleShape)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Capture")
                    }

                    // Add Task FAB
                    FloatingActionButton(
                        onClick = { showAddTaskSheet = true },
                        containerColor = NeonCyan,
                        contentColor = CoreOnPrimary,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("fab_add_task")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Task")
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
            // Stats summary card (inline minimal visual)
            HorizontalStatsSummary(stats = stats)

            Spacer(modifier = Modifier.height(16.dp))

            // Folders Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PROJECT FOLDERS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = GreyText
                    )
                )
                Text(
                    "+ Create",
                    modifier = Modifier
                        .clickable { showAddCategoryDialog = true }
                        .testTag("btn_create_project")
                        .padding(4.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Project folder Row selection
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("folder_shelf")
            ) {
                item {
                    FolderChip(
                        name = "All Spaces",
                        color = NeonCyan,
                        isSelected = selectedCategoryFilter == null,
                        onClick = { viewModel.selectedCategoryFilter.value = null }
                    )
                }
                items(categories) { cat ->
                    FolderChip(
                        name = cat.name,
                        color = Color(cat.color),
                        isSelected = selectedCategoryFilter == cat.id,
                        onClick = { viewModel.selectedCategoryFilter.value = cat.id },
                        onLongClick = { viewModel.deleteCategory(cat) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Search to-dos...", color = GreyText, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = GreyText) },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("search_field"),
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = PremiumGlassBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = DeepSurface,
                    unfocusedContainerColor = DeepSurface
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Priority filter tags row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Priority:", style = MaterialTheme.typography.bodySmall, color = GreyText)
                PriorityFilterChip(
                    label = "Any",
                    isSelected = priorityFilter == null,
                    onClick = { viewModel.priorityFilter.value = null },
                    selectedColor = NeonCyan
                )
                PriorityFilterChip(
                    label = "High",
                    isSelected = priorityFilter == 2,
                    onClick = { viewModel.priorityFilter.value = 2 },
                    selectedColor = NeonAmber
                )
                PriorityFilterChip(
                    label = "Medium",
                    isSelected = priorityFilter == 1,
                    onClick = { viewModel.priorityFilter.value = 1 },
                    selectedColor = NeonTeal
                )
                PriorityFilterChip(
                    label = "Low",
                    isSelected = priorityFilter == 0,
                    onClick = { viewModel.priorityFilter.value = 0 },
                    selectedColor = NeonCyan
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Task List Header
            Text(
                "TASKS (${filteredTasks.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = GreyText
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tasks List
            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AssignmentTurnedIn,
                            contentDescription = "Empty",
                            modifier = Modifier.size(56.dp),
                            tint = GreyText.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Nothing in this cosmic void.",
                            color = GreyText,
                            fontSize = 14.sp
                        )
                        Text(
                            "Tap + to create a task",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("tasks_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        val matchedCategory = categories.find { it.id == task.categoryId }
                        SwipeableTaskCard(
                            task = task,
                            category = matchedCategory,
                            onToggleComplete = { viewModel.completeTaskToggle(task) },
                            onDelete = { viewModel.deleteTask(task) },
                            onLinkToCalendar = { viewModel.linkTaskToDeviceCalendar(context, task) }
                        )
                    }
                }
            }
        }
    }

    // Bottom Modal Sheets & Overlay Dialogs
    if (showAddTaskSheet) {
        val presetText = voiceTextState
        AddTaskBottomSheet(
            presetTitleText = presetText,
            categories = categories,
            onDismiss = {
                showAddTaskSheet = false
                viewModel.currentVoiceText.value = null // Clear voice pre-load
            },
            onTaskCreated = { t, desc, due, pri, catId, recur, recurrencePat, rem ->
                viewModel.addTask(t, desc, due, pri, catId, recur, recurrencePat, rem)
                showAddTaskSheet = false
                viewModel.currentVoiceText.value = null
            }
        )
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onSave = { name, color ->
                viewModel.addCategory(name, color)
                showAddCategoryDialog = false
            }
        )
    }

    if (showAnalyticsDialog) {
        AnalyticsView(
            stats = stats,
            categories = categories,
            onDismiss = { showAnalyticsDialog = false }
        )
    }

    if (showCalendarDialog) {
        CalendarSheet(
            tasks = tasks,
            categories = categories,
            onDismiss = { showCalendarDialog = false }
        )
    }
    }
}

@Composable
fun HorizontalStatsSummary(stats: TodoStats) {
    val animatedProgress = animateFloatAsState(
        targetValue = stats.completionRatePercentage.toFloat() / 100f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "HorizontalProgress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stats_summary_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0x33381E72), Color.Transparent)
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "PROJECT STATUS",
                        fontSize = 12.sp,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "${stats.completedTasks}/${stats.totalTasks} Done",
                        fontSize = 11.sp,
                        color = GreyText,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = if (animatedProgress.value.isNaN() || animatedProgress.value < 0f) 0f else if (animatedProgress.value > 1f) 1f else animatedProgress.value)
                                .clip(RoundedCornerShape(3.dp))
                                .background(NeonCyan)
                        )
                    }

                    Text(
                        text = "${stats.completionRatePercentage}%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "You have ${stats.pendingTasks} pending vector objectives",
                    fontSize = 11.sp,
                    color = GreyText
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FolderChip(
    name: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .height(38.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) NeonCyan else PremiumGlassBg
        ),
        border = if (isSelected) null else BorderStroke(1.dp, PremiumGlassBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) CoreOnPrimary else color)
            )
            Text(
                name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) CoreOnPrimary else CompliantWhite
            )
        }
    }
}

@Composable
fun PriorityFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = if (isSelected) selectedColor.copy(alpha = 0.20f) else PremiumGlassBg,
        border = BorderStroke(1.dp, if (isSelected) selectedColor else PremiumGlassBorder)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) selectedColor else CompliantWhite,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun SwipeableTaskCard(
    task: Task,
    category: Category?,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onLinkToCalendar: () -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val priorityColor = when (task.priority) {
        2 -> NeonAmber // High
        1 -> NeonTeal  // Medium
        else -> NeonCyan // Low
    }

    val priorityText = when (task.priority) {
        2 -> "High"
        1 -> "Medium"
        else -> "Low"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .testTag("task_item_${task.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) Color(0xFF1C1B1F).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (task.isCompleted) Color(0xFF49454F).copy(alpha = 0.40f)
            else if (isExpanded) priorityColor.copy(alpha = 0.8f)
            else Color.White.copy(alpha = 0.09f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Custom Gorgeous Checked State Circle Icon
                    IconButton(
                        onClick = onToggleComplete,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("check_btn")
                    ) {
                        Surface(
                            modifier = Modifier.size(22.dp),
                            color = if (task.isCompleted) NeonCyan else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(2.dp, if (task.isCompleted) NeonCyan else Color.White.copy(alpha = 0.4f))
                        ) {
                            if (task.isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Checked",
                                    tint = CoreOnPrimary,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .padding(2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = task.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (task.isCompleted) GreyText else Color.White,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!task.isCompleted && task.dueDate != null) {
                            val formatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
                            val formatted = formatter.format(Date(task.dueDate))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = "Due Date",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    formatted,
                                    fontSize = 11.sp,
                                    color = NeonCyan
                                )
                            }
                        }
                    }
                }

                // If unexpanded and has priority, show a clean, gorgeous indicator dot
                if (!isExpanded) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(priorityColor)
                    )
                }
            }

            // Expanded detail section
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    if (task.description.isNotBlank()) {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = CompliantWhite.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 12.dp))

                    // Metadata row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Category Badge
                            category?.let {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(it.color).copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, Color(it.color).copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = it.name.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(it.color),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Priority Indicator (gorgeous filled background like in HTML!)
                            val priorityBgColor = when (task.priority) {
                                2 -> Color(0xFF381E72) // Matches design HTML high priority badge
                                else -> priorityColor.copy(alpha = 0.15f)
                            }
                            val priorityTextColor = when (task.priority) {
                                2 -> NeonCyan // #D0BCFF text on #381E72 high-contrast container
                                else -> priorityColor
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = priorityBgColor),
                                border = if (task.priority != 2) BorderStroke(1.dp, priorityColor.copy(alpha = 0.5f)) else null,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = priorityText.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = priorityTextColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            // Recurrence indicator
                            if (task.isRecurring && task.recurrencePattern != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = NeonTeal.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, NeonTeal.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Icon(Icons.Default.Autorenew, contentDescription = "Recurring", modifier = Modifier.size(11.dp), tint = NeonTeal)
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = task.recurrencePattern.uppercase(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonTeal
                                        )
                                    }
                                }
                            }
                        }

                        // Toolbar actions
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onLinkToCalendar,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.AddAlarm, contentDescription = "Sync to calendar", tint = NeonCyan, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = NeonAmber, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskBottomSheet(
    presetTitleText: String?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onTaskCreated: (
        title: String,
        description: String,
        dueDate: Long?,
        priority: Int,
        categoryId: Int?,
        isRecurring: Boolean,
        recurrencePattern: String?,
        reminderTime: Long?
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(presetTitleText ?: "") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(1) } // Medium default
    var selectedCategoryId by remember { mutableStateOf<Int?>(null) }
    
    var isRecurring by remember { mutableStateOf(false) }
    var recurrencePattern by remember { mutableStateOf("DAILY") }

    // Calendar & Alarm triggers
    var selectedDueDate by remember { mutableStateOf<Long?>(null) }
    var selectedReminderTime by remember { mutableStateOf<Long?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SolidSurfaceBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderGrey) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "⚡ NEW VECTOR OBJECTIVE",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = NeonCyan
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Task title Input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Task Title", color = GreyText) },
                modifier = Modifier.fillMaxWidth().testTag("add_title_field"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = BorderGrey,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Description Input
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Notes & details...", color = GreyText) },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = BorderGrey,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Folder selector shelf
            Text("FOLDER CATEGORY", style = MaterialTheme.typography.labelSmall, color = GreyText)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FolderChip(
                        name = "None",
                        color = BorderGrey,
                        isSelected = selectedCategoryId == null,
                        onClick = { selectedCategoryId = null }
                    )
                }
                items(categories) { cat ->
                    FolderChip(
                        name = cat.name,
                        color = Color(cat.color),
                        isSelected = selectedCategoryId == cat.id,
                        onClick = { selectedCategoryId = cat.id }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Priority selectors
            Text("PRIORITY RANGE", style = MaterialTheme.typography.labelSmall, color = GreyText)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Low", "Medium", "High").forEachIndexed { index, name ->
                    val isSelected = priority == index
                    val chipColor = when (index) {
                        2 -> NeonAmber
                        1 -> NeonTeal
                        else -> NeonCyan
                    }
                    Button(
                        onClick = { priority = index },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) chipColor.copy(alpha = 0.2f) else Color.Transparent,
                            contentColor = if (isSelected) chipColor else GreyText
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isSelected) chipColor else BorderGrey),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Date Picker trigger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Due date selector
                Button(
                    onClick = {
                        val calendar = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _: DatePicker, year: Int, month: Int, day: Int ->
                                val dueCal = Calendar.getInstance()
                                dueCal.set(year, month, day)
                                selectedDueDate = dueCal.timeInMillis
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BorderGrey),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    val label = if (selectedDueDate != null) {
                        SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(selectedDueDate!!))
                    } else "Add Due Date"
                    Icon(Icons.Default.Today, contentDescription = "Due Date picker", modifier = Modifier.size(16.dp), tint = NeonCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(label, fontSize = 11.sp)
                }

                // Reminder notification time picker
                Button(
                    onClick = {
                        val calendar = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _: DatePicker, year: Int, month: Int, day: Int ->
                                val remCal = Calendar.getInstance()
                                remCal.set(Calendar.YEAR, year)
                                remCal.set(Calendar.MONTH, month)
                                remCal.set(Calendar.DAY_OF_MONTH, day)
                                TimePickerDialog(
                                    context,
                                    { _, hour: Int, minute: Int ->
                                        remCal.set(Calendar.HOUR_OF_DAY, hour)
                                        remCal.set(Calendar.MINUTE, minute)
                                        remCal.set(Calendar.SECOND, 0)
                                        selectedReminderTime = remCal.timeInMillis
                                    },
                                    12, 0, false
                                ).show()
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BorderGrey),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    val label = if (selectedReminderTime != null) {
                        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(selectedReminderTime!!))
                    } else "Set Reminder"
                    Icon(Icons.Default.Notifications, contentDescription = "Alarm time picker", modifier = Modifier.size(16.dp), tint = NeonAmber)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(label, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recurring task Options
            Card(
                colors = CardDefaults.cardColors(containerColor = BorderGrey.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BorderGrey)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Repeat Recurring Sync", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Reschedule objective on complete", fontSize = 11.sp, color = GreyText)
                    }
                    Switch(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = NeonCyan.copy(alpha = 0.3f))
                    )
                }

                if (isRecurring) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("DAILY", "WEEKLY", "MONTHLY").forEach { pattern ->
                            val active = recurrencePattern == pattern
                            Button(
                                onClick = { recurrencePattern = pattern },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) NeonTeal.copy(alpha = 0.15f) else Color.Transparent
                                ),
                                border = BorderStroke(1.dp, if (active) NeonTeal else Color.Transparent),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(pattern, fontSize = 10.sp, color = if (active) NeonTeal else GreyText)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Confirm submission
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onTaskCreated(
                            title,
                            description,
                            selectedDueDate,
                            priority,
                            selectedCategoryId,
                            isRecurring,
                            if (isRecurring) recurrencePattern else null,
                            selectedReminderTime
                        )
                    } else {
                        Toast.makeText(context, "Please enter a valid title", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_submit_task"),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("RECORD OBJECTIVE ⚡", color = MidnightBg, fontWeight = FontWeight.Black)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// Add Custom Category/Folder Dialog
@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, colorValue: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val colorsList = listOf(
        0xFF00E5FF, // Cyan
        0xFFFF9100, // Amber / Orange
        0xFF1DE9B6, // Teal
        0xFFE040FB, // Magenta
        0xFFFF5252, // Soft Red
        0xFF64FFDA  // Spearmint green
    )
    var selectedColorIndex by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Spatial Folder", fontWeight = FontWeight.Bold, color = NeonCyan) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder Name", color = GreyText) },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderGrey,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_project_field")
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Vector Palette Color", fontSize = 12.sp, color = GreyText)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colorsList.forEachIndexed { idx, col ->
                        val isSel = selectedColorIndex == idx
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(col))
                                .border(
                                    width = 2.dp,
                                    color = if (isSel) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorIndex = idx }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, colorsList[selectedColorIndex].toInt())
                    }
                },
                modifier = Modifier.testTag("submit_project_btn")
            ) {
                Text("SAVE FOLDER", color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = GreyText)
            }
        },
        containerColor = SolidSurfaceBg,
        shape = RoundedCornerShape(16.dp)
    )
}

// Custom Draw Canvas Analytics popup
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsView(
    stats: TodoStats,
    categories: List<Category>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "📈 PRODUCTIVITY ANALYTICS",
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = NeonCyan
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (stats.totalTasks == 0) {
                    Text("No metrics collected yet.", color = GreyText)
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(160.dp).heightIn(max = 140.dp).fillMaxWidth()
                    ) {
                        // Drawing custom multi-colored completion arc visualization!
                        Canvas(modifier = Modifier.size(120.dp)) {
                            // Completed arc (neon cyan)
                            val completedFraction = stats.completedTasks.toFloat() / stats.totalTasks
                            
                            // Track base grey
                            drawCircle(
                                color = BorderGrey,
                                radius = size.width / 2.2f,
                                style = Stroke(width = 12.dp.toPx())
                            )

                            // Glowing Sweep Arc
                            drawArc(
                                color = NeonCyan,
                                startAngle = -90f,
                                sweepAngle = completedFraction * 360f,
                                useCenter = false,
                                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${stats.completionRatePercentage}%",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonCyan
                            )
                            Text(
                                "EFFICIENCY",
                                fontSize = 9.sp,
                                color = GreyText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Detail text lists
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total", fontSize = 12.sp, color = GreyText)
                            Text("${stats.totalTasks}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Resolved", fontSize = 12.sp, color = GreyText)
                            Text("${stats.completedTasks}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Pending", fontSize = 12.sp, color = GreyText)
                            Text("${stats.pendingTasks}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonAmber)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(color = BorderGrey)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "FOLDER BREAKDOWNS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreyText,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Folder status rows
                    categories.forEach { folder ->
                        val taskTotal = stats.categoryTaskCounts[folder.id] ?: 0
                        val taskDone = stats.categoryCompletedCounts[folder.id] ?: 0
                        val rate = if (taskTotal > 0) (taskDone * 100) / taskTotal else 0

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(folder.color)))
                                Text(folder.name, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            }
                            Text("$taskDone/$taskTotal ($rate%)", fontSize = 11.sp, color = Color(folder.color), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("close_analytics_btn")) {
                Text("CLOSE METRICS", color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = SolidSurfaceBg,
        shape = RoundedCornerShape(16.dp)
    )
}

// Beautiful Custom Minimal Monthly Calendar View Dialog
@Composable
fun CalendarSheet(
    tasks: List<Task>,
    categories: List<Category>,
    onDismiss: () -> Unit
) {
    val calendar = Calendar.getInstance()
    var currentYear by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableStateOf(calendar.get(Calendar.MONTH)) } // 0-indexed

    // Calculate details
    val daysInMonth = remember(currentYear, currentMonth) {
        val cal = Calendar.getInstance()
        cal.set(currentYear, currentMonth, 1)
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val firstDayOfWeek = remember(currentYear, currentMonth) {
        val cal = Calendar.getInstance()
        cal.set(currentYear, currentMonth, 1)
        cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday ... 7 = Saturday
    }

    val monthName = remember(currentMonth) {
        listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )[currentMonth]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (currentMonth == 0) {
                        currentMonth = 11
                        currentYear--
                    } else {
                        currentMonth--
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Previous Month", tint = NeonCyan)
                }

                Text(
                    text = "$monthName $currentYear".uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )

                IconButton(onClick = {
                    if (currentMonth == 11) {
                        currentMonth = 0
                        currentYear++
                    } else {
                        currentMonth++
                    }
                }) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Next Month", tint = NeonCyan)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Day headings
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                        Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreyText)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Grid calculation
                val totalCells = daysInMonth + firstDayOfWeek - 1
                val rows = (totalCells + 6) / 7

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in 0 until rows) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            for (c in 0 until 7) {
                                val cellIdx = r * 7 + c
                                val dayNum = cellIdx - firstDayOfWeek + 2

                                if (dayNum in 1..daysInMonth) {
                                    // Check if we have active pending task due on this exact day
                                    val tasksOnDay = tasks.filter { t ->
                                        if (t.dueDate != null && !t.isCompleted) {
                                            val tCal = Calendar.getInstance().apply { timeInMillis = t.dueDate }
                                            tCal.get(Calendar.YEAR) == currentYear &&
                                                    tCal.get(Calendar.MONTH) == currentMonth &&
                                                    tCal.get(Calendar.DAY_OF_MONTH) == dayNum
                                        } else false
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (tasksOnDay.isNotEmpty()) NeonCyan.copy(alpha = 0.15f) else Color.Transparent)
                                            .border(
                                                width = 1.dp,
                                                color = if (tasksOnDay.isNotEmpty()) NeonCyan else BorderGrey,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                    ) {
                                        Text(
                                            text = "$dayNum",
                                            fontSize = 11.sp,
                                            fontWeight = if (tasksOnDay.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                                            color = if (tasksOnDay.isNotEmpty()) NeonCyan else Color.White,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        // Bullet points
                                        if (tasksOnDay.isNotEmpty()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                tasksOnDay.take(3).forEach { t ->
                                                    val cat = categories.find { it.id == t.categoryId }
                                                    val color = cat?.color?.let { Color(it) } ?: NeonCyan
                                                    Box(
                                                        modifier = Modifier
                                                            .size(3.dp)
                                                            .clip(CircleShape)
                                                            .background(color)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Empty padding space Cell
                                    Box(modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("close_calendar_btn")) {
                Text("CLOSE CALENDAR", color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = SolidSurfaceBg,
        shape = RoundedCornerShape(16.dp)
    )
}

// Simple color helper
val Transparent = Color(0x00000000)
