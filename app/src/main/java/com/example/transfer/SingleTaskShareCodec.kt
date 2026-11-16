package com.example.transfer

import android.util.Base64
import com.example.data.LocalTaskRecord
import com.example.model.TaskItem
import com.example.model.TaskPriority
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@Serializable
data class SharedTaskPayload(
  val version: Int = 1,
  val title: String,
  val notes: String = "",
  val deadlineMillis: Long,
  val priority: String = TaskPriority.MEDIUM.wireValue,
  val category: String = "工作",
  val remindMinutesBefore: Long = 15L,
) {
  fun toNewLocalRecord(): LocalTaskRecord {
    val now = System.currentTimeMillis()
    return LocalTaskRecord(
      id = "local_${UUID.randomUUID().toString().replace("-", "").take(16)}",
      title = title.trim().take(120),
      notes = notes.trim().take(500),
      deadlineMillis = deadlineMillis,
      isCompleted = false,
      completedAtMillis = 0L,
      priority = TaskPriority.fromWire(priority).wireValue,
      category = category.trim().ifBlank { "工作" }.take(20),
      remindMinutesBefore = remindMinutesBefore.coerceIn(0L, 1440L),
      createdAtMillis = now,
      updatedAtMillis = now,
    )
  }
}

object SingleTaskShareCodec {
  const val TOKEN_PREFIX = "TT1:"
  private val json = Json { ignoreUnknownKeys = true }

  fun fromTaskItem(task: TaskItem): LocalTaskRecord = LocalTaskRecord(
    id = task.id,
    title = task.title,
    notes = task.notes,
    deadlineMillis = task.deadlineMillis,
    isCompleted = false,
    completedAtMillis = 0L,
    priority = task.priority,
    category = task.category,
    remindMinutesBefore = task.remindMinutesBefore,
    createdAtMillis = task.createdAtMillis,
    updatedAtMillis = task.updatedAtMillis,
  )

  fun encodeToken(task: TaskItem): String {
    val payload = SharedTaskPayload(
      version = 1,
      title = task.title,
      notes = task.notes,
      deadlineMillis = task.deadlineMillis,
      priority = task.priority,
      category = task.category,
      remindMinutesBefore = task.remindMinutesBefore,
    )
    val jsonBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
    val compressed = ByteArrayOutputStream().use { bos ->
      GZIPOutputStream(bos).use { it.write(jsonBytes) }
      bos.toByteArray()
    }
    val b64 = Base64.encodeToString(compressed, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    return "$TOKEN_PREFIX$b64"
  }

  fun decodeToken(raw: String): Result<SharedTaskPayload> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
      return Result.failure(IllegalArgumentException("分享内容为空"))
    }
    if (trimmed.startsWith("{")) {
      return runCatching {
        val payload = json.decodeFromString<SharedTaskPayload>(trimmed)
        require(payload.title.isNotBlank()) { "任务标题为空" }
        payload
      }
    }
    if (!trimmed.startsWith(TOKEN_PREFIX)) {
      return Result.failure(IllegalArgumentException("无法识别的分享码或二维码（需以 TT1: 开头或为 6 位数字配对码）"))
    }
    return try {
      val compressed = Base64.decode(
        trimmed.removePrefix(TOKEN_PREFIX),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
      )
      val jsonStr = GZIPInputStream(ByteArrayInputStream(compressed)).use {
        it.readBytes().toString(Charsets.UTF_8)
      }
      val payload = json.decodeFromString<SharedTaskPayload>(jsonStr)
      if (payload.title.isBlank()) {
        Result.failure(IllegalArgumentException("分享的任务内容为空"))
      } else {
        Result.success(payload)
      }
    } catch (e: Exception) {
      Result.failure(IllegalStateException("分享口令或二维码无效或已损坏"))
    }
  }
}
