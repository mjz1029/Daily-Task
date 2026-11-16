package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.TaskItem
import com.example.util.DeadlineFormatter

object TaskReminderScheduler {
  const val CHANNEL_ID = "today_task_deadlines"
  private const val EXTRA_TASK_ID = "extra_task_id"
  private const val EXTRA_TASK_TITLE = "extra_task_title"
  private const val EXTRA_DEADLINE_MILLIS = "extra_deadline_millis"

  fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(NotificationManager::class.java) ?: return
      val existing = manager.getNotificationChannel(CHANNEL_ID)
      if (existing == null) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          "今日工作截止提醒",
          NotificationManager.IMPORTANCE_HIGH,
        ).apply {
          description = "在今天的工作任务接近截止时间时发出提醒"
        }
        manager.createNotificationChannel(channel)
      }
    }
  }

  fun syncReminders(context: Context, tasks: List<TaskItem>) {
    ensureNotificationChannel(context)
    val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
    val now = System.currentTimeMillis()

    tasks.forEach { task ->
      val requestCode = task.id.hashCode()
      val intent = Intent(context, TaskReminderReceiver::class.java).apply {
        putExtra(EXTRA_TASK_ID, task.id)
        putExtra(EXTRA_TASK_TITLE, task.title)
        putExtra(EXTRA_DEADLINE_MILLIS, task.deadlineMillis)
      }
      val pendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      if (task.isCompleted || task.deadlineMillis <= 0L) {
        alarmManager.cancel(pendingIntent)
        return@forEach
      }

      val triggerAt = task.deadlineMillis - (task.remindMinutesBefore * 60_000L)
      if (triggerAt > now) {
        alarmManager.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerAt,
          pendingIntent,
        )
      } else {
        alarmManager.cancel(pendingIntent)
      }
    }
  }

  fun showDigestNotification(context: Context, unfinishedCount: Int, overdueCount: Int) {
    ensureNotificationChannel(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) return
    }

    val openAppIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val contentIntent = PendingIntent.getActivity(
      context,
      9001,
      openAppIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val title = if (unfinishedCount == 0) {
      "今日工作已全部完成"
    } else {
      "今天还有 $unfinishedCount 项工作待完成"
    }
    val body = when {
      unfinishedCount == 0 -> "干得漂亮，可以随时添加新的工作安排。"
      overdueCount > 0 -> "其中 $overdueCount 项已超过完成时限，请及时处理。"
      else -> "点击查看每项工作的完成时限与倒计时。"
    }

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification_check)
      .setContentTitle(title)
      .setContentText(body)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(contentIntent)
      .build()

    NotificationManagerCompat.from(context).notify(9001, notification)
  }

  class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      ensureNotificationChannel(context)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
      }

      val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
      val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "工作待办"
      val deadlineMillis = intent.getLongExtra(EXTRA_DEADLINE_MILLIS, 0L)

      val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val contentIntent = PendingIntent.getActivity(
        context,
        taskId.hashCode(),
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      val deadlineText = DeadlineFormatter.formatDeadlineFull(deadlineMillis)
      val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_check)
        .setContentTitle("待办即将截止：$taskTitle")
        .setContentText("完成时限：$deadlineText")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(contentIntent)
        .build()

      NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
    }
  }
}
