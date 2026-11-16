package com.example.model

import java.util.Calendar

enum class TaskPriority(val wireValue: String, val labelZh: String) {
  HIGH("high", "紧急"),
  MEDIUM("medium", "常规"),
  LOW("low", "暂缓");

  companion object {
    fun fromWire(value: String): TaskPriority =
      entries.firstOrNull { it.wireValue == value } ?: MEDIUM
  }
}

data class TaskItem(
  val id: String = "",
  val title: String = "",
  val notes: String = "",
  val deadlineMillis: Long = 0L,
  val isCompleted: Boolean = false,
  val completedAtMillis: Long = 0L,
  val priority: String = TaskPriority.MEDIUM.wireValue,
  val category: String = "工作",
  val remindMinutesBefore: Long = 15L,
  val createdAtMillis: Long = 0L,
  val updatedAtMillis: Long = 0L,
) {
  val priorityLevel: TaskPriority
    get() = TaskPriority.fromWire(priority)

  fun isOverdue(nowMillis: Long = System.currentTimeMillis()): Boolean =
    !isCompleted && deadlineMillis > 0L && deadlineMillis < nowMillis

  fun isDueToday(nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (deadlineMillis <= 0L) return false
    val (startOfDay, endOfDay) = dayBounds(nowMillis)
    return deadlineMillis in startOfDay..endOfDay
  }

  fun isCompletedToday(nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (!isCompleted || completedAtMillis <= 0L) return false
    val (startOfDay, endOfDay) = dayBounds(nowMillis)
    return completedAtMillis in startOfDay..endOfDay
  }

  fun belongsToTodayActionList(nowMillis: Long = System.currentTimeMillis()): Boolean {
    val (_, endOfDay) = dayBounds(nowMillis)
    return if (!isCompleted) {
      deadlineMillis <= endOfDay
    } else {
      isDueToday(nowMillis) || isCompletedToday(nowMillis)
    }
  }

  companion object {
    fun dayBounds(anchorMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
      val cal = Calendar.getInstance().apply {
        timeInMillis = anchorMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
      val start = cal.timeInMillis
      cal.set(Calendar.HOUR_OF_DAY, 23)
      cal.set(Calendar.MINUTE, 59)
      cal.set(Calendar.SECOND, 59)
      cal.set(Calendar.MILLISECOND, 999)
      return start to cal.timeInMillis
    }
  }
}
