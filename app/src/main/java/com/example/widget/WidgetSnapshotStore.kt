package com.example.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.TaskItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.widgetDataStore by preferencesDataStore(name = "today_tasks_widget_store")

@Serializable
data class WidgetTaskEntry(
  val id: String,
  val title: String,
  val deadlineMillis: Long,
  val priority: String,
  val category: String,
  val isCompleted: Boolean = false,
)

@Serializable
data class WidgetTasksState(
  val unfinishedTodayCount: Int = 0,
  val completedTodayCount: Int = 0,
  val overdueCount: Int = 0,
  val lastSyncMillis: Long = 0L,
  val tasks: List<WidgetTaskEntry> = emptyList(),
)

object WidgetSnapshotStore {
  private val STATE_KEY = stringPreferencesKey("widget_state_json")
  private val json = Json { ignoreUnknownKeys = true }

  fun observeState(context: Context): Flow<WidgetTasksState> =
    context.applicationContext.widgetDataStore.data.map { prefs ->
      val raw = prefs[STATE_KEY]
      if (raw.isNullOrBlank()) {
        WidgetTasksState()
      } else {
        runCatching { json.decodeFromString<WidgetTasksState>(raw) }
          .getOrDefault(WidgetTasksState())
      }
    }

  suspend fun getState(context: Context): WidgetTasksState =
    observeState(context).first()

  suspend fun saveFromTasks(
    context: Context,
    allTasks: List<TaskItem>,
    nowMillis: Long = System.currentTimeMillis(),
  ) {
    val (_, endOfDay) = TaskItem.dayBounds(nowMillis)
    val unfinishedForToday = allTasks
      .filter { !it.isCompleted && it.deadlineMillis <= endOfDay }
      .sortedWith(
        compareBy<TaskItem> { it.deadlineMillis }
          .thenByDescending { it.priority == "high" }
      )
    val overdueCount = unfinishedForToday.count { it.isOverdue(nowMillis) }
    val completedTodayCount = allTasks.count {
      it.isCompletedToday(nowMillis) || (it.isCompleted && it.isDueToday(nowMillis))
    }

    val state = WidgetTasksState(
      unfinishedTodayCount = unfinishedForToday.size,
      completedTodayCount = completedTodayCount,
      overdueCount = overdueCount,
      lastSyncMillis = nowMillis,
      tasks = unfinishedForToday.take(15).map { task ->
        WidgetTaskEntry(
          id = task.id,
          title = task.title,
          deadlineMillis = task.deadlineMillis,
          priority = task.priority,
          category = task.category,
          isCompleted = task.isCompleted,
        )
      },
    )
    context.applicationContext.widgetDataStore.edit { prefs ->
      prefs[STATE_KEY] = json.encodeToString(state)
    }
  }

  suspend fun markTaskCompletedOptimistic(context: Context, taskId: String) {
    val now = System.currentTimeMillis()
    context.applicationContext.widgetDataStore.edit { prefs ->
      val raw = prefs[STATE_KEY] ?: return@edit
      val current = runCatching { json.decodeFromString<WidgetTasksState>(raw) }.getOrNull() ?: return@edit
      val remaining = current.tasks.filterNot { it.id == taskId }
      val updated = current.copy(
        unfinishedTodayCount = (current.unfinishedTodayCount - 1).coerceAtLeast(0),
        completedTodayCount = current.completedTodayCount + 1,
        overdueCount = remaining.count { it.deadlineMillis in 1 until now },
        lastSyncMillis = now,
        tasks = remaining,
      )
      prefs[STATE_KEY] = json.encodeToString(updated)
    }
  }
}
