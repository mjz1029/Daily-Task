package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.TaskItem
import com.example.model.TaskPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.localTasksDataStore by preferencesDataStore(name = "local_tasks_store")

@Serializable
data class LocalTaskRecord(
  val id: String,
  val title: String,
  val notes: String = "",
  val deadlineMillis: Long,
  val isCompleted: Boolean = false,
  val completedAtMillis: Long = 0L,
  val priority: String = TaskPriority.MEDIUM.wireValue,
  val category: String = "工作",
  val remindMinutesBefore: Long = 15L,
  val createdAtMillis: Long = System.currentTimeMillis(),
  val updatedAtMillis: Long = System.currentTimeMillis(),
) {
  fun toTaskItem(): TaskItem = TaskItem(
    id = id,
    title = title,
    notes = notes,
    deadlineMillis = deadlineMillis,
    isCompleted = isCompleted,
    completedAtMillis = completedAtMillis,
    priority = priority,
    category = category,
    remindMinutesBefore = remindMinutesBefore,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
  )
}

class LocalTasksStore(context: Context) {
  private val appContext = context.applicationContext
  private val json = Json { ignoreUnknownKeys = true }

  fun observeTasks(): Flow<List<TaskItem>> =
    appContext.localTasksDataStore.data.map { prefs ->
      decodeRecords(prefs[TASKS_KEY])
        .map { it.toTaskItem() }
        .sortedWith(
          compareBy<TaskItem> { it.isCompleted }
            .thenBy { it.deadlineMillis }
            .thenByDescending { it.priority == TaskPriority.HIGH.wireValue }
        )
    }

  suspend fun getAllOnce(): List<TaskItem> = observeTasks().first()

  suspend fun getAllRecordsOnce(): List<LocalTaskRecord> {
    val prefs = appContext.localTasksDataStore.data.first()
    return decodeRecords(prefs[TASKS_KEY])
  }

  suspend fun createTask(
    title: String,
    notes: String,
    deadlineMillis: Long,
    priority: TaskPriority,
    category: String,
    remindMinutesBefore: Long,
  ): String {
    val now = System.currentTimeMillis()
    val newId = "local_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val record = LocalTaskRecord(
      id = newId,
      title = title.trim().take(120),
      notes = notes.trim().take(500),
      deadlineMillis = deadlineMillis,
      isCompleted = false,
      completedAtMillis = 0L,
      priority = priority.wireValue,
      category = category.trim().ifBlank { "工作" }.take(20),
      remindMinutesBefore = remindMinutesBefore.coerceIn(0L, 1440L),
      createdAtMillis = now,
      updatedAtMillis = now,
    )
    appContext.localTasksDataStore.edit { prefs ->
      val current = decodeRecords(prefs[TASKS_KEY]).toMutableList()
      current.add(record)
      prefs[TASKS_KEY] = json.encodeToString(current)
    }
    return newId
  }

  suspend fun updateTask(task: TaskItem) {
    val now = System.currentTimeMillis()
    appContext.localTasksDataStore.edit { prefs ->
      val current = decodeRecords(prefs[TASKS_KEY]).toMutableList()
      val idx = current.indexOfFirst { it.id == task.id }
      if (idx >= 0) {
        current[idx] = current[idx].copy(
          title = task.title.trim().take(120),
          notes = task.notes.trim().take(500),
          deadlineMillis = task.deadlineMillis,
          isCompleted = task.isCompleted,
          completedAtMillis = task.completedAtMillis,
          priority = task.priority,
          category = task.category.trim().ifBlank { "工作" }.take(20),
          remindMinutesBefore = task.remindMinutesBefore.coerceIn(0L, 1440L),
          updatedAtMillis = now,
        )
        prefs[TASKS_KEY] = json.encodeToString(current)
      }
    }
  }

  suspend fun toggleTaskCompletion(taskId: String, isCompleted: Boolean) {
    val now = System.currentTimeMillis()
    appContext.localTasksDataStore.edit { prefs ->
      val current = decodeRecords(prefs[TASKS_KEY]).toMutableList()
      val idx = current.indexOfFirst { it.id == taskId }
      if (idx >= 0) {
        current[idx] = current[idx].copy(
          isCompleted = isCompleted,
          completedAtMillis = if (isCompleted) now else 0L,
          updatedAtMillis = now,
        )
        prefs[TASKS_KEY] = json.encodeToString(current)
      }
    }
  }

  suspend fun deleteTask(taskId: String) {
    appContext.localTasksDataStore.edit { prefs ->
      val current = decodeRecords(prefs[TASKS_KEY]).filterNot { it.id == taskId }
      prefs[TASKS_KEY] = json.encodeToString(current)
    }
  }

  suspend fun importRecords(incoming: List<LocalTaskRecord>, replaceExisting: Boolean): Int {
    if (incoming.isEmpty() && !replaceExisting) return 0
    var importedCount = 0
    appContext.localTasksDataStore.edit { prefs ->
      val base = if (replaceExisting) {
        mutableListOf()
      } else {
        decodeRecords(prefs[TASKS_KEY]).toMutableList()
      }
      val existingFingerprints = base.map { "${it.id}|${it.title}|${it.deadlineMillis}" }.toMutableSet()
      val existingIds = base.map { it.id }.toMutableSet()

      incoming.forEach { raw ->
        val cleanTitle = raw.title.trim().take(120)
        if (cleanTitle.isBlank()) return@forEach
        val fingerprint = "${raw.id}|$cleanTitle|${raw.deadlineMillis}"
        if (!replaceExisting && fingerprint in existingFingerprints) return@forEach

        val safeId = if (raw.id.isBlank() || raw.id in existingIds) {
          "local_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        } else {
          raw.id
        }
        val normalized = raw.copy(
          id = safeId,
          title = cleanTitle,
          notes = raw.notes.trim().take(500),
          category = raw.category.trim().ifBlank { "工作" }.take(20),
          remindMinutesBefore = raw.remindMinutesBefore.coerceIn(0L, 1440L),
        )
        base.add(normalized)
        existingIds.add(safeId)
        existingFingerprints.add(fingerprint)
        importedCount++
      }
      prefs[TASKS_KEY] = json.encodeToString(base)
    }
    return importedCount
  }

  suspend fun clearAll() {
    appContext.localTasksDataStore.edit { prefs ->
      prefs.remove(TASKS_KEY)
    }
  }

  private fun decodeRecords(raw: String?): List<LocalTaskRecord> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { json.decodeFromString<List<LocalTaskRecord>>(raw) }
      .getOrDefault(emptyList())
  }

  private companion object {
    val TASKS_KEY = stringPreferencesKey("local_tasks_json")
  }
}
