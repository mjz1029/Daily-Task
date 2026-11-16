package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DeadlineFormatter {
  private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
  private val dateTimeFormat = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)
  private val shortDateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.CHINA)

  fun formatDeadlineClock(deadlineMillis: Long): String {
    if (deadlineMillis <= 0L) return "未设时限"
    return timeFormat.format(Date(deadlineMillis))
  }

  fun formatDeadlineFull(deadlineMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    if (deadlineMillis <= 0L) return "未设截止时间"
    val calTarget = Calendar.getInstance().apply { timeInMillis = deadlineMillis }
    val calNow = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val sameDay =
      calTarget.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
        calTarget.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
    if (sameDay) {
      return "今天 ${timeFormat.format(Date(deadlineMillis))}"
    }
    calNow.add(Calendar.DAY_OF_YEAR, 1)
    val isTomorrow =
      calTarget.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
        calTarget.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
    if (isTomorrow) {
      return "明天 ${timeFormat.format(Date(deadlineMillis))}"
    }
    return dateTimeFormat.format(Date(deadlineMillis))
  }

  fun formatShortDeadline(deadlineMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    if (deadlineMillis <= 0L) return "--:--"
    val calTarget = Calendar.getInstance().apply { timeInMillis = deadlineMillis }
    val calNow = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val sameDay =
      calTarget.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
        calTarget.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) {
      timeFormat.format(Date(deadlineMillis))
    } else {
      shortDateFormat.format(Date(deadlineMillis))
    }
  }

  fun formatRemainingLabel(
    deadlineMillis: Long,
    isCompleted: Boolean,
    nowMillis: Long = System.currentTimeMillis(),
  ): String {
    if (isCompleted) return "已完成"
    if (deadlineMillis <= 0L) return "未设时限"
    val diff = deadlineMillis - nowMillis
    return if (diff < 0L) {
      val overdueMins = (-diff) / 60_000L
      when {
        overdueMins < 60L -> "已逾期 ${overdueMins.coerceAtLeast(1)} 分钟"
        overdueMins < 1440L -> "已逾期 ${overdueMins / 60L} 小时"
        else -> "已逾期 ${overdueMins / 1440L} 天"
      }
    } else {
      val remainMins = diff / 60_000L
      when {
        remainMins < 1L -> "即将到期"
        remainMins < 60L -> "剩余 $remainMins 分钟"
        remainMins < 1440L -> {
          val h = remainMins / 60L
          val m = remainMins % 60L
          if (m == 0L) "剩余 ${h} 小时" else "剩余 ${h}小时${m}分"
        }
        else -> "剩余 ${remainMins / 1440L} 天"
      }
    }
  }

  fun formatSyncTime(syncMillis: Long): String {
    if (syncMillis <= 0L) return "等待同步"
    return "同步于 ${timeFormat.format(Date(syncMillis))}"
  }

  fun todayAt(hour: Int, minute: Int, baseMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
      timeInMillis = baseMillis
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, minute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
  }

  fun tomorrowAt(hour: Int, minute: Int, baseMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
      timeInMillis = baseMillis
      add(Calendar.DAY_OF_YEAR, 1)
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, minute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
  }
}
