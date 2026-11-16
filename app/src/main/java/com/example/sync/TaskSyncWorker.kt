package com.example.sync

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.LocalTasksStore
import com.example.notifications.TaskReminderScheduler
import com.example.widget.TodayTasksWidget
import com.example.widget.WidgetSnapshotStore
import java.util.concurrent.TimeUnit

class TaskSyncWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val localTasks = LocalTasksStore(applicationContext).getAllOnce()
    WidgetSnapshotStore.saveFromTasks(
      context = applicationContext,
      allTasks = localTasks,
    )
    TaskReminderScheduler.syncReminders(applicationContext, localTasks)
    TodayTasksWidget().updateAll(applicationContext)
    return Result.success()
  }

  companion object {
    private const val PERIODIC_WORK_NAME = "today_tasks_periodic_refresh"

    fun schedulePeriodic(context: Context) {
      val request = PeriodicWorkRequestBuilder<TaskSyncWorker>(15, TimeUnit.MINUTES).build()
      WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
        PERIODIC_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
      )
    }
  }
}
