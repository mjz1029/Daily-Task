package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.ai.AppPreferencesStore
import com.example.ai.FoldableDisplayPreference
import com.example.data.LocalTasksStore
import com.example.model.TaskItem
import com.example.notifications.TaskReminderScheduler
import com.example.sync.TaskSyncWorker
import com.example.ui.adaptive.FoldablePostureMode
import com.example.ui.adaptive.rememberFoldableLayoutMetrics
import com.example.ui.components.AiQuickCaptureBar
import com.example.ui.components.FoldableLayoutModeSelector
import com.example.ui.components.TaskCard
import com.example.ui.components.TaskEditorContent
import com.example.ui.components.TodayFocusHeader
import com.example.viewmodel.AiParseUiState
import com.example.viewmodel.TaskFilter
import com.example.viewmodel.TaskViewModel
import com.example.viewmodel.UiState
import com.example.widget.TodayTasksWidget
import com.example.widget.WidgetSnapshotStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksWorkspaceScreen(
  openAddTaskOnLaunch: Boolean,
  onConsumedOpenAddTask: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val viewModel: TaskViewModel = viewModel(
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        TaskViewModel(
          localStore = LocalTasksStore(app),
          preferencesStore = AppPreferencesStore(app),
        )
      }
    },
  )

  val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
  val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
  val selectedTaskId by viewModel.selectedTaskId.collectAsStateWithLifecycle()
  val isCreatingNewTask by viewModel.isCreatingNewTask.collectAsStateWithLifecycle()
  val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
  val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
  val llmConfig by viewModel.llmConfig.collectAsStateWithLifecycle()
  val aiParseState by viewModel.aiParseState.collectAsStateWithLifecycle()
  val pendingAiDraft by viewModel.pendingAiDraft.collectAsStateWithLifecycle()
  val foldablePreference by viewModel.foldablePreference.collectAsStateWithLifecycle()

  var showSettingsSheet by remember { mutableStateOf(false) }
  var showCompactEditorSheet by remember { mutableStateOf(false) }
  var aiBarExpandedOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
  var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { }

  LaunchedEffect(Unit) {
    TaskSyncWorker.schedulePeriodic(context)
    while (true) {
      delay(30_000L)
      nowMillis = System.currentTimeMillis()
    }
  }

  LaunchedEffect(openAddTaskOnLaunch) {
    if (openAddTaskOnLaunch) {
      viewModel.startNewTaskDraft()
      showCompactEditorSheet = true
      onConsumedOpenAddTask()
    }
  }

  LaunchedEffect(actionMessage) {
    val msg = actionMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.consumeActionMessage()
    }
  }

  val allTasks = (tasksState as? UiState.Success)?.data ?: emptyList()
  LaunchedEffect(tasksState) {
    if (tasksState is UiState.Success) {
      WidgetSnapshotStore.saveFromTasks(
        context = context,
        allTasks = allTasks,
        nowMillis = System.currentTimeMillis(),
      )
      TaskReminderScheduler.syncReminders(context, allTasks)
      runCatching { TodayTasksWidget().updateAll(context) }
    }
  }

  val (_, endOfToday) = remember(nowMillis) { TaskItem.dayBounds(nowMillis) }
  val unfinishedTodayTasks = remember(allTasks, nowMillis) {
    allTasks.filter { !it.isCompleted && it.deadlineMillis <= endOfToday }
      .sortedWith(compareBy<TaskItem> { it.deadlineMillis }.thenByDescending { it.priority == "high" })
  }
  val completedTodayCount = remember(allTasks, nowMillis) {
    allTasks.count { it.isCompletedToday(nowMillis) || (it.isCompleted && it.isDueToday(nowMillis)) }
  }
  val overdueCount = remember(unfinishedTodayTasks, nowMillis) {
    unfinishedTodayTasks.count { it.isOverdue(nowMillis) }
  }
  val nextUrgentTask = unfinishedTodayTasks.firstOrNull()

  val filteredTasks = remember(allTasks, selectedFilter, nowMillis) {
    when (selectedFilter) {
      TaskFilter.TODAY_UNFINISHED -> unfinishedTodayTasks
      TaskFilter.URGENT_SOON -> unfinishedTodayTasks.filter {
        it.isOverdue(nowMillis) || (it.deadlineMillis - nowMillis) in 0..(2 * 60 * 60 * 1000L)
      }
      TaskFilter.TODAY_COMPLETED -> allTasks.filter {
        it.isCompleted && (it.isCompletedToday(nowMillis) || it.isDueToday(nowMillis))
      }.sortedByDescending { it.completedAtMillis }
      TaskFilter.ALL_SCHEDULED -> allTasks
    }
  }

  val selectedTask = remember(allTasks, selectedTaskId) {
    allTasks.firstOrNull { it.id == selectedTaskId }
  }

  fun requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val metrics = rememberFoldableLayoutMetrics(
      availableWidthDp = maxWidth,
      availableHeightDp = maxHeight,
      userPreference = foldablePreference,
    )

    val hasPersistentEditorPane =
      metrics.mode == FoldablePostureMode.BOOK_TWO_PANE ||
        metrics.mode == FoldablePostureMode.TOP_BOTTOM_SPLIT

    // On outer cover screen & tabletop, collapse quick-capture into a compact FAB by default;
    // on fullscreen unfolded view, show expanded by default with a shrink button.
    val isAiQuickBarExpanded =
      aiBarExpandedOverride ?: (metrics.mode == FoldablePostureMode.FULLSCREEN_EXPANDED)

    BackHandler(
      enabled = showCompactEditorSheet || showSettingsSheet ||
        (hasPersistentEditorPane && selectedTask != null) ||
        (!hasPersistentEditorPane && isAiQuickBarExpanded && metrics.mode != FoldablePostureMode.FULLSCREEN_EXPANDED)
    ) {
      when {
        showSettingsSheet -> showSettingsSheet = false
        showCompactEditorSheet -> {
          showCompactEditorSheet = false
          viewModel.closeEditor()
        }
        hasPersistentEditorPane && selectedTask != null -> viewModel.closeEditor()
        else -> aiBarExpandedOverride = false
      }
    }

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      containerColor = MaterialTheme.colorScheme.background,
      topBar = {
        TopAppBar(
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
          ),
          title = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "今日待办",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
              )
              FoldableLayoutModeSelector(
                postureBadgeLabel = metrics.postureBadgeLabel,
                isExpandedFoldable = metrics.isExpandedFoldable,
                currentPreference = foldablePreference,
                onSelectPreference = viewModel::setFoldablePreference,
              )
            }
          },
          actions = {
            IconButton(
              onClick = { showSettingsSheet = true },
              modifier = Modifier.testTag("open_settings_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          },
        )
      },
      floatingActionButton = {
        if (!hasPersistentEditorPane) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (!isAiQuickBarExpanded) {
              ExtendedFloatingActionButton(
                onClick = { aiBarExpandedOverride = true },
                icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                text = { Text("智能速记") },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag("expand_ai_quick_bar_fab"),
              )
            }
            ExtendedFloatingActionButton(
              onClick = {
                viewModel.startNewTaskDraft()
                showCompactEditorSheet = true
              },
              icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
              text = { Text("随时添加") },
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.testTag("add_task_fab"),
            )
          }
        }
      },
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        when (val state = tasksState) {
          is UiState.Loading -> {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                  text = "正在加载本机待办…",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          is UiState.Error -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer,
              ) {
                Column(
                  modifier = Modifier.padding(20.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = "读取待办遇到问题",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                  )
                  Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                  )
                }
              }
            }
          }

          is UiState.Success -> {
            when (metrics.mode) {
              FoldablePostureMode.TOP_BOTTOM_SPLIT -> {
                Column(modifier = Modifier.fillMaxSize()) {
                  Column(
                    modifier = Modifier
                      .weight(metrics.topPaneRatio)
                      .fillMaxWidth()
                      .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    TodayFocusHeader(
                      unfinishedTodayCount = unfinishedTodayTasks.size,
                      completedTodayCount = completedTodayCount,
                      overdueCount = overdueCount,
                      nextUrgentTask = nextUrgentTask,
                      nowMillis = nowMillis,
                      compactMode = true,
                    )

                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Box(modifier = Modifier.weight(1f)) {
                        FilterChipRow(
                          selectedFilter = selectedFilter,
                          onSelectFilter = viewModel::setFilter,
                        )
                      }
                      FilterChip(
                        selected = false,
                        onClick = { viewModel.setFoldablePreference(FoldableDisplayPreference.FULLSCREEN) },
                        label = { Text("全屏") },
                        leadingIcon = {
                          Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = "切换为全屏展示",
                            modifier = Modifier.size(16.dp),
                          )
                        },
                        modifier = Modifier.testTag("quick_switch_fullscreen_chip"),
                      )
                    }

                    TaskListColumn(
                      tasks = filteredTasks,
                      selectedTaskId = selectedTask?.id,
                      selectedFilter = selectedFilter,
                      nowMillis = nowMillis,
                      onToggleComplete = viewModel::toggleTaskCompletion,
                      onSelectTask = { task -> viewModel.selectTask(task.id) },
                      modifier = Modifier.weight(1f),
                      bottomContentPadding = 8.dp,
                    )
                  }

                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(metrics.hingeGapHeightDp.coerceAtLeast(12.dp)),
                    contentAlignment = Alignment.Center,
                  ) {
                    Box(
                      modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    )
                    Surface(
                      shape = RoundedCornerShape(999.dp),
                      color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                      Box(
                        modifier = Modifier
                          .width(40.dp)
                          .height(4.dp),
                      )
                    }
                  }

                  Surface(
                    modifier = Modifier
                      .weight(1f - metrics.topPaneRatio)
                      .fillMaxWidth()
                      .padding(horizontal = 16.dp)
                      .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 1.dp,
                  ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(horizontal = 18.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Text(
                          text = if (selectedTask == null || isCreatingNewTask) "上下分栏 · 新建与智能速记" else "上下分栏 · 任务详情与截止修改",
                          style = MaterialTheme.typography.labelLarge,
                          color = MaterialTheme.colorScheme.primary,
                        )
                        if (selectedTask != null && !isCreatingNewTask) {
                          FilterChip(
                            selected = false,
                            onClick = { viewModel.startNewTaskDraft() },
                            label = { Text("+ 新建工作") },
                            modifier = Modifier.testTag("switch_to_new_task_chip"),
                          )
                        }
                      }

                      TaskEditorContent(
                        editingTask = if (isCreatingNewTask) null else selectedTask,
                        pendingAiDraft = pendingAiDraft,
                        onConsumeAiDraft = viewModel::consumePendingAiDraft,
                        llmBadgeLabel = llmConfig.badgeLabel,
                        isAiParsing = aiParseState is AiParseUiState.Parsing,
                        onAiParseFill = { sentence ->
                          viewModel.parseSentence(sentence = sentence, autoCreate = false)
                        },
                        isSaving = isSaving,
                        showCloseButton = selectedTask != null && !isCreatingNewTask,
                        onClose = { viewModel.closeEditor() },
                        onSaveNew = { title, notes, deadline, priority, category, remind ->
                          if (remind > 0L) requestNotificationPermissionIfNeeded()
                          viewModel.createTask(
                            title = title,
                            notes = notes,
                            deadlineMillis = deadline,
                            priority = priority,
                            category = category,
                            remindMinutesBefore = remind,
                          )
                        },
                        onUpdateExisting = { existing, title, notes, deadline, priority, category, remind ->
                          if (remind > 0L) requestNotificationPermissionIfNeeded()
                          viewModel.updateTask(
                            existing = existing,
                            title = title,
                            notes = notes,
                            deadlineMillis = deadline,
                            priority = priority,
                            category = category,
                            remindMinutesBefore = remind,
                          )
                        },
                        onDelete = { taskId -> viewModel.deleteTask(taskId) },
                        modifier = Modifier.weight(1f),
                      )
                    }
                  }
                }
              }

              FoldablePostureMode.FULLSCREEN_EXPANDED -> {
                Row(modifier = Modifier.fillMaxSize()) {
                  if (metrics.showNavigationRail) {
                    WorkspaceNavRail(
                      selectedFilter = selectedFilter,
                      onSelectFilter = viewModel::setFilter,
                      onAddNew = {
                        viewModel.startNewTaskDraft()
                        showCompactEditorSheet = true
                      },
                    )
                  }
                  Box(
                    modifier = Modifier
                      .weight(1f)
                      .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                  ) {
                    Column(
                      modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                      verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      TodayFocusHeader(
                        unfinishedTodayCount = unfinishedTodayTasks.size,
                        completedTodayCount = completedTodayCount,
                        overdueCount = overdueCount,
                        nextUrgentTask = nextUrgentTask,
                        nowMillis = nowMillis,
                        compactMode = false,
                      )

                      AnimatedVisibility(
                        visible = isAiQuickBarExpanded,
                        enter = fadeIn(tween(150)) + expandVertically(tween(180)),
                        exit = fadeOut(tween(120)) + shrinkVertically(tween(150)),
                      ) {
                        AiQuickCaptureBar(
                          llmConfig = llmConfig,
                          parseState = aiParseState,
                          compact = false,
                          onCollapse = { aiBarExpandedOverride = false },
                          onParseToFill = { sentence, clear ->
                            viewModel.parseSentence(
                              sentence = sentence,
                              autoCreate = false,
                              onDraftReady = {
                                clear()
                                showCompactEditorSheet = true
                              },
                            )
                          },
                          onParseAndCreate = { sentence, clear ->
                            requestNotificationPermissionIfNeeded()
                            viewModel.parseSentence(
                              sentence = sentence,
                              autoCreate = true,
                              onTaskCreated = clear,
                            )
                          },
                          onClearError = viewModel::clearAiError,
                        )
                      }

                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                      ) {
                        Box(modifier = Modifier.weight(1f)) {
                          FilterChipRow(
                            selectedFilter = selectedFilter,
                            onSelectFilter = viewModel::setFilter,
                          )
                        }
                        if (metrics.isUnfoldedPortrait) {
                          FilterChip(
                            selected = false,
                            onClick = { viewModel.setFoldablePreference(FoldableDisplayPreference.TOP_BOTTOM_SPLIT) },
                            label = { Text("上下布局") },
                            leadingIcon = {
                              Icon(
                                imageVector = Icons.Rounded.Splitscreen,
                                contentDescription = "切换为上下布局",
                                modifier = Modifier.size(16.dp),
                              )
                            },
                            modifier = Modifier.testTag("quick_switch_top_bottom_chip"),
                          )
                        }
                      }

                      TaskListColumn(
                        tasks = filteredTasks,
                        selectedTaskId = null,
                        selectedFilter = selectedFilter,
                        nowMillis = nowMillis,
                        onToggleComplete = viewModel::toggleTaskCompletion,
                        onSelectTask = { task ->
                          viewModel.selectTask(task.id)
                          showCompactEditorSheet = true
                        },
                        modifier = Modifier.weight(1f),
                        bottomContentPadding = 88.dp,
                      )
                    }
                  }
                }
              }

              FoldablePostureMode.BOOK_TWO_PANE -> {
                Row(modifier = Modifier.fillMaxSize()) {
                  if (metrics.showNavigationRail) {
                    WorkspaceNavRail(
                      selectedFilter = selectedFilter,
                      onSelectFilter = viewModel::setFilter,
                      onAddNew = { viewModel.startNewTaskDraft() },
                    )
                  }

                  Column(
                    modifier = Modifier
                      .weight(metrics.leftPaneRatio)
                      .fillMaxHeight()
                      .padding(start = 16.dp, end = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    TodayFocusHeader(
                      unfinishedTodayCount = unfinishedTodayTasks.size,
                      completedTodayCount = completedTodayCount,
                      overdueCount = overdueCount,
                      nextUrgentTask = nextUrgentTask,
                      nowMillis = nowMillis,
                      compactMode = true,
                    )

                    if (!metrics.showNavigationRail) {
                      FilterChipRow(
                        selectedFilter = selectedFilter,
                        onSelectFilter = viewModel::setFilter,
                      )
                    }

                    TaskListColumn(
                      tasks = filteredTasks,
                      selectedTaskId = selectedTask?.id,
                      selectedFilter = selectedFilter,
                      nowMillis = nowMillis,
                      onToggleComplete = viewModel::toggleTaskCompletion,
                      onSelectTask = { task -> viewModel.selectTask(task.id) },
                      modifier = Modifier.weight(1f),
                    )
                  }

                  Box(
                    modifier = Modifier
                      .width(metrics.hingeBoundsWidthDp)
                      .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                  ) {
                    Box(
                      modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.92f)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    )
                  }

                  Surface(
                    modifier = Modifier
                      .weight(1f - metrics.leftPaneRatio)
                      .fillMaxHeight()
                      .padding(start = 4.dp, end = 16.dp, bottom = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 1.dp,
                  ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Text(
                          text = if (selectedTask == null || isCreatingNewTask) "新建与智能速记" else "任务详情与截止修改",
                          style = MaterialTheme.typography.labelLarge,
                          color = MaterialTheme.colorScheme.primary,
                        )
                        if (selectedTask != null && !isCreatingNewTask) {
                          FilterChip(
                            selected = false,
                            onClick = { viewModel.startNewTaskDraft() },
                            label = { Text("+ 新建工作") },
                            modifier = Modifier.testTag("switch_to_new_task_chip"),
                          )
                        }
                      }

                      TaskEditorContent(
                        editingTask = if (isCreatingNewTask) null else selectedTask,
                        pendingAiDraft = pendingAiDraft,
                        onConsumeAiDraft = viewModel::consumePendingAiDraft,
                        llmBadgeLabel = llmConfig.badgeLabel,
                        isAiParsing = aiParseState is AiParseUiState.Parsing,
                        onAiParseFill = { sentence ->
                          viewModel.parseSentence(sentence = sentence, autoCreate = false)
                        },
                        isSaving = isSaving,
                        showCloseButton = selectedTask != null && !isCreatingNewTask,
                        onClose = { viewModel.closeEditor() },
                        onSaveNew = { title, notes, deadline, priority, category, remind ->
                          if (remind > 0L) requestNotificationPermissionIfNeeded()
                          viewModel.createTask(
                            title = title,
                            notes = notes,
                            deadlineMillis = deadline,
                            priority = priority,
                            category = category,
                            remindMinutesBefore = remind,
                          )
                        },
                        onUpdateExisting = { existing, title, notes, deadline, priority, category, remind ->
                          if (remind > 0L) requestNotificationPermissionIfNeeded()
                          viewModel.updateTask(
                            existing = existing,
                            title = title,
                            notes = notes,
                            deadlineMillis = deadline,
                            priority = priority,
                            category = category,
                            remindMinutesBefore = remind,
                          )
                        },
                        onDelete = { taskId -> viewModel.deleteTask(taskId) },
                        modifier = Modifier.weight(1f),
                      )
                    }
                  }
                }
              }

              FoldablePostureMode.TABLETOP_FLEX -> {
                Column(
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                ) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height((metrics.hingeTopHeightDp - 56.dp).coerceAtLeast(160.dp)),
                    contentAlignment = Alignment.Center,
                  ) {
                    TodayFocusHeader(
                      unfinishedTodayCount = unfinishedTodayTasks.size,
                      completedTodayCount = completedTodayCount,
                      overdueCount = overdueCount,
                      nextUrgentTask = nextUrgentTask,
                      nowMillis = nowMillis,
                      compactMode = true,
                    )
                  }

                  Spacer(modifier = Modifier.height(metrics.hingeGapHeightDp))

                  Column(
                    modifier = Modifier
                      .weight(1f)
                      .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    AnimatedVisibility(
                      visible = isAiQuickBarExpanded,
                      enter = fadeIn(tween(150)) + expandVertically(tween(180)),
                      exit = fadeOut(tween(120)) + shrinkVertically(tween(150)),
                    ) {
                      AiQuickCaptureBar(
                        llmConfig = llmConfig,
                        parseState = aiParseState,
                        compact = true,
                        onCollapse = { aiBarExpandedOverride = false },
                        onParseToFill = { sentence, clear ->
                          viewModel.parseSentence(
                            sentence = sentence,
                            autoCreate = false,
                            onDraftReady = {
                              clear()
                              showCompactEditorSheet = true
                            },
                          )
                        },
                        onParseAndCreate = { sentence, clear ->
                          requestNotificationPermissionIfNeeded()
                          viewModel.parseSentence(
                            sentence = sentence,
                            autoCreate = true,
                            onTaskCreated = {
                              clear()
                              aiBarExpandedOverride = false
                            },
                          )
                        },
                        onClearError = viewModel::clearAiError,
                      )
                    }
                    FilterChipRow(
                      selectedFilter = selectedFilter,
                      onSelectFilter = viewModel::setFilter,
                    )
                    TaskListColumn(
                      tasks = filteredTasks,
                      selectedTaskId = selectedTask?.id,
                      selectedFilter = selectedFilter,
                      nowMillis = nowMillis,
                      onToggleComplete = viewModel::toggleTaskCompletion,
                      onSelectTask = { task ->
                        viewModel.selectTask(task.id)
                        showCompactEditorSheet = true
                      },
                      modifier = Modifier.weight(1f),
                      bottomContentPadding = 88.dp,
                    )
                  }
                }
              }

              FoldablePostureMode.COVER_COMPACT -> {
                Column(
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  TodayFocusHeader(
                    unfinishedTodayCount = unfinishedTodayTasks.size,
                    completedTodayCount = completedTodayCount,
                    overdueCount = overdueCount,
                    nextUrgentTask = nextUrgentTask,
                    nowMillis = nowMillis,
                    compactMode = true,
                  )

                  AnimatedVisibility(
                    visible = isAiQuickBarExpanded,
                    enter = fadeIn(tween(150)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(150)),
                  ) {
                    AiQuickCaptureBar(
                      llmConfig = llmConfig,
                      parseState = aiParseState,
                      compact = true,
                      onCollapse = { aiBarExpandedOverride = false },
                      onParseToFill = { sentence, clear ->
                        viewModel.parseSentence(
                          sentence = sentence,
                          autoCreate = false,
                          onDraftReady = {
                            clear()
                            showCompactEditorSheet = true
                          },
                        )
                      },
                      onParseAndCreate = { sentence, clear ->
                        requestNotificationPermissionIfNeeded()
                        viewModel.parseSentence(
                          sentence = sentence,
                          autoCreate = true,
                          onTaskCreated = {
                            clear()
                            aiBarExpandedOverride = false
                          },
                        )
                      },
                      onClearError = viewModel::clearAiError,
                    )
                  }

                  FilterChipRow(
                    selectedFilter = selectedFilter,
                    onSelectFilter = viewModel::setFilter,
                  )

                  TaskListColumn(
                    tasks = filteredTasks,
                    selectedTaskId = null,
                    selectedFilter = selectedFilter,
                    nowMillis = nowMillis,
                    onToggleComplete = viewModel::toggleTaskCompletion,
                    onSelectTask = { task ->
                      viewModel.selectTask(task.id)
                      showCompactEditorSheet = true
                    },
                    modifier = Modifier.weight(1f),
                    bottomContentPadding = 88.dp,
                  )
                }
              }
            }
          }
        }
      }
    }

    if (showCompactEditorSheet) {
      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      ModalBottomSheet(
        onDismissRequest = {
          showCompactEditorSheet = false
          viewModel.closeEditor()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      ) {
        TaskEditorContent(
          editingTask = if (isCreatingNewTask) null else selectedTask,
          pendingAiDraft = pendingAiDraft,
          onConsumeAiDraft = viewModel::consumePendingAiDraft,
          llmBadgeLabel = llmConfig.badgeLabel,
          isAiParsing = aiParseState is AiParseUiState.Parsing,
          onAiParseFill = { sentence ->
            viewModel.parseSentence(sentence = sentence, autoCreate = false)
          },
          isSaving = isSaving,
          showCloseButton = true,
          onClose = {
            scope.launch {
              sheetState.hide()
              showCompactEditorSheet = false
              viewModel.closeEditor()
            }
          },
          onSaveNew = { title, notes, deadline, priority, category, remind ->
            if (remind > 0L) requestNotificationPermissionIfNeeded()
            viewModel.createTask(
              title = title,
              notes = notes,
              deadlineMillis = deadline,
              priority = priority,
              category = category,
              remindMinutesBefore = remind,
              onSaved = {
                scope.launch {
                  sheetState.hide()
                  showCompactEditorSheet = false
                }
              },
            )
          },
          onUpdateExisting = { existing, title, notes, deadline, priority, category, remind ->
            if (remind > 0L) requestNotificationPermissionIfNeeded()
            viewModel.updateTask(
              existing = existing,
              title = title,
              notes = notes,
              deadlineMillis = deadline,
              priority = priority,
              category = category,
              remindMinutesBefore = remind,
              onUpdated = {
                scope.launch {
                  sheetState.hide()
                  showCompactEditorSheet = false
                }
              },
            )
          },
          onDelete = { taskId ->
            viewModel.deleteTask(taskId) {
              scope.launch {
                sheetState.hide()
                showCompactEditorSheet = false
              }
            }
          },
          modifier = Modifier.padding(bottom = 16.dp),
        )
      }
    }

    if (showSettingsSheet) {
      val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      ModalBottomSheet(
        onDismissRequest = { showSettingsSheet = false },
        sheetState = settingsSheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      ) {
        SettingsSheetContent(
          llmConfig = llmConfig,
          totalLocalTaskCount = allTasks.size,
          unfinishedTodayCount = unfinishedTodayTasks.size,
          overdueCount = overdueCount,
          postureLabel = metrics.postureBadgeLabel,
          onTestLlmConnection = viewModel::testLlmConnection,
          onSaveLlmConfig = { updated -> viewModel.saveLlmConfig(updated) },
          onLoadRecordsForTransfer = viewModel::getLocalRecordsForTransfer,
          onImportRecords = viewModel::importTransferredRecords,
          onNotifyMessage = { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
          },
          onDismiss = { showSettingsSheet = false },
        )
      }
    }
  }
}

@Composable
private fun WorkspaceNavRail(
  selectedFilter: TaskFilter,
  onSelectFilter: (TaskFilter) -> Unit,
  onAddNew: () -> Unit,
) {
  NavigationRail(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Spacer(modifier = Modifier.height(8.dp))
    FloatingActionButton(
      onClick = onAddNew,
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
      modifier = Modifier.testTag("rail_add_task_button"),
    ) {
      Icon(Icons.Rounded.Add, contentDescription = "新建工作")
    }
    Spacer(modifier = Modifier.height(16.dp))
    TaskFilter.entries.forEach { filter ->
      NavigationRailItem(
        selected = selectedFilter == filter,
        onClick = { onSelectFilter(filter) },
        icon = {
          Icon(
            imageVector = filterIcon(filter),
            contentDescription = filter.label,
          )
        },
        label = { Text(filter.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
      )
    }
  }
}

@Composable
private fun FilterChipRow(
  selectedFilter: TaskFilter,
  onSelectFilter: (TaskFilter) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    TaskFilter.entries.forEach { filter ->
      FilterChip(
        selected = selectedFilter == filter,
        onClick = { onSelectFilter(filter) },
        label = { Text(filter.label) },
        leadingIcon = {
          Icon(
            imageVector = filterIcon(filter),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        },
        modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}"),
      )
    }
  }
}

private fun filterIcon(filter: TaskFilter) = when (filter) {
  TaskFilter.TODAY_UNFINISHED -> Icons.Rounded.Today
  TaskFilter.URGENT_SOON -> Icons.Rounded.Schedule
  TaskFilter.TODAY_COMPLETED -> Icons.Rounded.CheckCircleOutline
  TaskFilter.ALL_SCHEDULED -> Icons.Rounded.AllInclusive
}

@Composable
private fun TaskListColumn(
  tasks: List<TaskItem>,
  selectedTaskId: String?,
  selectedFilter: TaskFilter,
  nowMillis: Long,
  onToggleComplete: (TaskItem) -> Unit,
  onSelectTask: (TaskItem) -> Unit,
  modifier: Modifier = Modifier,
  bottomContentPadding: androidx.compose.ui.unit.Dp = 16.dp,
) {
  val listState = rememberLazyListState()
  LaunchedEffect(selectedFilter) {
    listState.scrollToItem(0)
  }

  // Single Box container prevents sibling weight(1f) competition & vertical jump during tab switches
  Box(modifier = modifier.fillMaxWidth()) {
    Crossfade(
      targetState = tasks.isEmpty(),
      animationSpec = tween(durationMillis = 150),
      label = "taskListEmptyStateCrossfade",
    ) { isEmpty ->
      if (isEmpty) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_all_done),
              contentDescription = "今日暂无未完成工作",
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .width(180.dp)
                .height(115.dp)
                .clip(RoundedCornerShape(18.dp)),
            )
            Text(
              text = when (selectedFilter) {
                TaskFilter.TODAY_UNFINISHED -> "今天的工作全部清零啦"
                TaskFilter.URGENT_SOON -> "近期两小时内无即将逾期任务"
                TaskFilter.TODAY_COMPLETED -> "今天还没有已勾选完成的任务"
                TaskFilter.ALL_SCHEDULED -> "当前还没有工作任务"
              },
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "试试「智能速记」或点击「随时添加」记录工作",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(bottom = bottomContentPadding),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(tasks, key = { it.id }) { task ->
            TaskCard(
              task = task,
              isSelected = task.id == selectedTaskId,
              nowMillis = nowMillis,
              onToggleComplete = { onToggleComplete(task) },
              onClick = { onSelectTask(task) },
            )
          }
        }
      }
    }
  }
}
