package com.example.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.example.model.TaskItem

object CalendarHelper {

  fun buildInsertEventIntent(task: TaskItem): Intent {
    val beginMillis = task.deadlineMillis
    val endMillis = beginMillis + 30 * 60_000L
    val description = buildString {
      if (task.notes.isNotBlank()) {
        append(task.notes.trim())
        append("\n\n")
      }
      append("分类：${task.category} · 紧急程度：${task.priorityLevel.labelZh}")
    }
    return Intent(Intent.ACTION_INSERT).apply {
      data = CalendarContract.Events.CONTENT_URI
      putExtra(CalendarContract.Events.TITLE, task.title)
      putExtra(CalendarContract.Events.DESCRIPTION, description)
      putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
      putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
      putExtra(CalendarContract.Events.HAS_ALARM, task.remindMinutesBefore > 0L)
    }
  }

  fun openSystemCalendarInsert(context: Context, task: TaskItem): Boolean {
    return try {
      val intent = buildInsertEventIntent(task)
      if (context !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
      true
    } catch (_: ActivityNotFoundException) {
      false
    } catch (_: Exception) {
      false
    }
  }
}
