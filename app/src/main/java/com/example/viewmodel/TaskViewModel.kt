package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AppPreferencesStore
import com.example.ai.FoldableDisplayPreference
import com.example.ai.LlmConfig
import com.example.ai.NaturalLanguageTaskParser
import com.example.ai.ParsedTaskDraft
import com.example.data.LocalTaskRecord
import com.example.data.LocalTasksStore
import com.example.model.TaskItem
import com.example.model.TaskPriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiParseUiState {
  data object Idle : AiParseUiState
  data object Parsing : AiParseUiState
  data class Error(val message: String) : AiParseUiState
}

enum class TaskFilter(val label: String) {
  TODAY_UNFINISHED("今日待办"),
  URGENT_SOON("即将截止"),
  TODAY_COMPLETED("今日已办"),
  ALL_SCHEDULED("全部日程"),
}

class TaskViewModel(
  private val localStore: LocalTasksStore,
  private val preferencesStore: AppPreferencesStore,
  private val taskParser: NaturalLanguageTaskParser = NaturalLanguageTaskParser(),
) : ViewModel() {

  val tasksState: StateFlow<UiState<List<TaskItem>>> =
    localStore.observeTasks()
      .map<List<TaskItem>, UiState<List<TaskItem>>> { tasks ->
        _lastUpdatedMillis.value = System.currentTimeMillis()
        UiState.Success(tasks)
      }
      .catch { error ->
        Log.w(TAG, "Error observing local tasks", error)
        emit(UiState.Error(error.message ?: "读取本机待办失败"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val llmConfig: StateFlow<LlmConfig> = preferencesStore.observeLlmConfig()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = LlmConfig(),
    )

  val foldablePreference: StateFlow<FoldableDisplayPreference> =
    preferencesStore.observeFoldablePreference()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = FoldableDisplayPreference.AUTO,
      )

  private val _selectedFilter = MutableStateFlow(TaskFilter.TODAY_UNFINISHED)
  val selectedFilter: StateFlow<TaskFilter> = _selectedFilter.asStateFlow()

  private val _selectedTaskId = MutableStateFlow<String?>(null)
  val selectedTaskId: StateFlow<String?> = _selectedTaskId.asStateFlow()

  private val _isCreatingNewTask = MutableStateFlow(false)
  val isCreatingNewTask: StateFlow<Boolean> = _isCreatingNewTask.asStateFlow()

  private val _isSaving = MutableStateFlow(false)
  val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

  private val _actionMessage = MutableStateFlow<String?>(null)
  val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

  private val _lastUpdatedMillis = MutableStateFlow(0L)
  val lastUpdatedMillis: StateFlow<Long> = _lastUpdatedMillis.asStateFlow()

  private val _aiParseState = MutableStateFlow<AiParseUiState>(AiParseUiState.Idle)
  val aiParseState: StateFlow<AiParseUiState> = _aiParseState.asStateFlow()

  private val _pendingAiDraft = MutableStateFlow<ParsedTaskDraft?>(null)
  val pendingAiDraft: StateFlow<ParsedTaskDraft?> = _pendingAiDraft.asStateFlow()

  private val _pendingMultiAiDrafts = MutableStateFlow<List<ParsedTaskDraft>>(emptyList())
  val pendingMultiAiDrafts: StateFlow<List<ParsedTaskDraft>> = _pendingMultiAiDrafts.asStateFlow()

  fun setFilter(filter: TaskFilter) {
    _selectedFilter.value = filter
  }

  fun setFoldablePreference(preference: FoldableDisplayPreference) {
    viewModelScope.launch {
      preferencesStore.saveFoldablePreference(preference)
      _actionMessage.value = "已切换为「${preference.labelZh}」展示模式"
    }
  }

  fun saveLlmConfig(config: LlmConfig, onSaved: () -> Unit = {}) {
    viewModelScope.launch {
      preferencesStore.saveLlmConfig(config)
      _aiParseState.value = AiParseUiState.Idle
      _actionMessage.value = "已保存大模型配置：${config.provider.shortLabel}"
      onSaved()
    }
  }

  suspend fun testLlmConnection(config: LlmConfig): Result<String> =
    taskParser.testConnection(config)

  fun clearAiError() {
    if (_aiParseState.value is AiParseUiState.Error) {
      _aiParseState.value = AiParseUiState.Idle
    }
  }

  fun consumePendingAiDraft() {
    _pendingAiDraft.value = null
  }

  fun clearMultiAiDrafts() {
    _pendingMultiAiDrafts.value = emptyList()
  }

  fun removeDraftFromBatch(index: Int) {
    val current = _pendingMultiAiDrafts.value
    if (index in current.indices) {
      _pendingMultiAiDrafts.value = current.toMutableList().apply { removeAt(index) }
    }
  }

  fun pickSingleDraftFromBatch(index: Int) {
    val current = _pendingMultiAiDrafts.value
    if (index !in current.indices) return
    val picked = current[index]
    _pendingMultiAiDrafts.value = current.toMutableList().apply { removeAt(index) }
    _selectedTaskId.value = null
    _isCreatingNewTask.value = true
    _pendingAiDraft.value = picked
  }

  fun createTasksFromDrafts(drafts: List<ParsedTaskDraft>, onSaved: () -> Unit = {}) {
    if (drafts.isEmpty()) return
    viewModelScope.launch {
      _isSaving.value = true
      val inputs = drafts.map { draft ->
        LocalTasksStore.NewTaskInput(
          title = draft.title,
          notes = draft.notes,
          deadlineMillis = draft.deadlineMillis,
          priority = draft.priority,
          category = draft.category,
          remindMinutesBefore = draft.remindMinutesBefore,
        )
      }
      val ids = localStore.createTasks(inputs)
      _isSaving.value = false
      _pendingMultiAiDrafts.value = emptyList()
      if (ids.isNotEmpty()) {
        _selectedTaskId.value = ids.first()
        _actionMessage.value = "已批量添加 ${ids.size} 项工作待办"
      }
      onSaved()
    }
  }

  fun parseSentence(
    sentence: String,
    autoCreate: Boolean,
    allowMultiplePreview: Boolean = false,
    onDraftReady: () -> Unit = {},
    onMultiDraftsReady: () -> Unit = {},
    onTaskCreated: () -> Unit = {},
  ) {
    val trimmed = sentence.trim()
    if (trimmed.isBlank()) {
      _aiParseState.value = AiParseUiState.Error("请先输入要记录的工作内容与时间")
      return
    }
    viewModelScope.launch {
      _aiParseState.value = AiParseUiState.Parsing
      val activeConfig = preferencesStore.getLlmConfigOnce()
      val parseResult = taskParser.parseSentenceMultiple(trimmed, activeConfig)
      parseResult.onSuccess { drafts ->
        _aiParseState.value = AiParseUiState.Idle
        if (autoCreate) {
          if (drafts.size == 1) {
            val draft = drafts.first()
            createTask(
              title = draft.title,
              notes = draft.notes,
              deadlineMillis = draft.deadlineMillis,
              priority = draft.priority,
              category = draft.category,
              remindMinutesBefore = draft.remindMinutesBefore,
              onSaved = {
                _actionMessage.value = "智能速记已创建「${draft.title}」"
                onTaskCreated()
              },
            )
          } else {
            createTasksFromDrafts(drafts) {
              _actionMessage.value = "智能速记已批量创建 ${drafts.size} 项待办"
              onTaskCreated()
            }
          }
        } else if (allowMultiplePreview && drafts.size > 1) {
          _pendingMultiAiDrafts.value = drafts
          _actionMessage.value = "已识别 ${drafts.size} 项待办，请确认添加"
          onMultiDraftsReady()
        } else {
          _selectedTaskId.value = null
          _isCreatingNewTask.value = true
          _pendingAiDraft.value = drafts.first()
          _actionMessage.value = "已智能填充工作内容、时限与分类，请确认"
          onDraftReady()
        }
      }.onFailure { err ->
        _aiParseState.value = AiParseUiState.Error(err.message ?: "解析失败，请重试")
      }
    }
  }

  fun selectTask(taskId: String?) {
    _pendingAiDraft.value = null
    _isCreatingNewTask.value = false
    _selectedTaskId.value = taskId
  }

  fun startNewTaskDraft() {
    _pendingAiDraft.value = null
    _selectedTaskId.value = null
    _isCreatingNewTask.value = true
  }

  fun closeEditor() {
    _pendingAiDraft.value = null
    _isCreatingNewTask.value = false
    _selectedTaskId.value = null
  }

  fun consumeActionMessage() {
    _actionMessage.value = null
  }

  suspend fun getLocalRecordsForTransfer(): List<LocalTaskRecord> =
    localStore.getAllRecordsOnce()

  suspend fun importTransferredRecords(records: List<LocalTaskRecord>, replaceExisting: Boolean): Int {
    val count = localStore.importRecords(records, replaceExisting)
    _actionMessage.value = if (count > 0) {
      "已成功接收并导入 $count 项工作待办"
    } else {
      "未发现需要新增的待办事项"
    }
    return count
  }

  fun createTask(
    title: String,
    notes: String,
    deadlineMillis: Long,
    priority: TaskPriority,
    category: String,
    remindMinutesBefore: Long,
    onSaved: (String) -> Unit = {},
  ) {
    if (title.isBlank()) {
      _actionMessage.value = "请填写工作内容"
      return
    }
    viewModelScope.launch {
      _isSaving.value = true
      val newId = localStore.createTask(
        title = title,
        notes = notes,
        deadlineMillis = deadlineMillis,
        priority = priority,
        category = category,
        remindMinutesBefore = remindMinutesBefore,
      )
      _isSaving.value = false
      _pendingAiDraft.value = null
      _isCreatingNewTask.value = false
      _selectedTaskId.value = newId
      _actionMessage.value = "已添加到本机与桌面小组件"
      onSaved(newId)
    }
  }

  fun updateTask(
    existing: TaskItem,
    title: String,
    notes: String,
    deadlineMillis: Long,
    priority: TaskPriority,
    category: String,
    remindMinutesBefore: Long,
    onUpdated: () -> Unit = {},
  ) {
    if (title.isBlank()) {
      _actionMessage.value = "工作内容不能为空"
      return
    }
    viewModelScope.launch {
      _isSaving.value = true
      val updated = existing.copy(
        title = title,
        notes = notes,
        deadlineMillis = deadlineMillis,
        priority = priority.wireValue,
        category = category,
        remindMinutesBefore = remindMinutesBefore,
      )
      localStore.updateTask(updated)
      _isSaving.value = false
      _actionMessage.value = "工作内容与截止时间已更新"
      onUpdated()
    }
  }

  fun toggleTaskCompletion(task: TaskItem) {
    val nextState = !task.isCompleted
    viewModelScope.launch {
      localStore.toggleTaskCompletion(task.id, nextState)
      _actionMessage.value = if (nextState) "已完成「${task.title}」" else "已恢复为待办"
    }
  }

  fun deleteTask(taskId: String, onDeleted: () -> Unit = {}) {
    viewModelScope.launch {
      _isSaving.value = true
      localStore.deleteTask(taskId)
      _isSaving.value = false
      if (_selectedTaskId.value == taskId) {
        _selectedTaskId.value = null
      }
      _actionMessage.value = "已删除该工作任务"
      onDeleted()
    }
  }

  private companion object {
    const val TAG = "TaskViewModel"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
