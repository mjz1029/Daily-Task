package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.data.LocalTasksStore
import com.example.util.DeadlineFormatter

class TodayTasksWidget : GlanceAppWidget() {

  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val initialState = WidgetSnapshotStore.getState(context)

    provideContent {
      val state by WidgetSnapshotStore.observeState(context).collectAsState(initial = initialState)
      GlanceTheme {
        TodayWidgetContent(context = context, state = state)
      }
    }
  }

  @Composable
  private fun TodayWidgetContent(context: Context, state: WidgetTasksState) {
    val now = System.currentTimeMillis()
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val openAddTaskIntent = Intent(context, MainActivity::class.java).apply {
      putExtra(MainActivity.EXTRA_OPEN_ADD_TASK, true)
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val surfaceBg = ColorProvider(Color(0xFFFAF9F5))
    val cardBg = ColorProvider(Color(0xFFFFFFFF))
    val jadePrimary = ColorProvider(Color(0xFF0E6B62))
    val jadeSoft = ColorProvider(Color(0xFFCBEFE9))
    val inkText = ColorProvider(Color(0xFF191C1B))
    val mutedText = ColorProvider(Color(0xFF4A5350))
    val urgentRed = ColorProvider(Color(0xFFC63A2C))
    val urgentBg = ColorProvider(Color(0xFFFFDAD5))
    val amberText = ColorProvider(Color(0xFFB95B10))
    val amberBg = ColorProvider(Color(0xFFFFE0C2))

    Column(
      modifier = GlanceModifier
        .fillMaxSize()
        .background(surfaceBg)
        .cornerRadius(22.dp)
        .padding(14.dp)
    ) {
      Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier = GlanceModifier
            .defaultWeight()
            .clickable(actionStartActivity(openAppIntent))
        ) {
          Text(
            text = if (state.unfinishedTodayCount == 0) {
              "今日工作已清零"
            } else {
              "今日未完成 · ${state.unfinishedTodayCount} 项"
            },
            style = TextStyle(
              color = inkText,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
          )
          val subtitle = when {
            state.overdueCount > 0 -> "${state.overdueCount} 项已逾期 · 已完成 ${state.completedTodayCount} 项"
            else -> "今日已完成 ${state.completedTodayCount} 项"
          }
          Text(
            text = subtitle,
            style = TextStyle(
              color = if (state.overdueCount > 0) urgentRed else mutedText,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
          )
        }

        Box(
          modifier = GlanceModifier
            .size(34.dp)
            .cornerRadius(17.dp)
            .background(ColorProvider(Color(0xFFECEAE1)))
            .clickable(actionRunCallback<RefreshWidgetAction>()),
          contentAlignment = Alignment.Center,
        ) {
          Image(
            provider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = "刷新桌面小组件",
            modifier = GlanceModifier.size(18.dp),
          )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        Box(
          modifier = GlanceModifier
            .height(34.dp)
            .cornerRadius(17.dp)
            .background(jadePrimary)
            .padding(horizontal = 12.dp)
            .clickable(actionStartActivity(openAddTaskIntent)),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "+ 添加",
            style = TextStyle(
              color = ColorProvider(Color.White),
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
            ),
          )
        }
      }

      Spacer(modifier = GlanceModifier.height(10.dp))

      if (state.tasks.isEmpty()) {
        Box(
          modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(jadeSoft)
            .clickable(actionStartActivity(openAddTaskIntent))
            .padding(16.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "今天暂无未完成工作 ✓",
              style = TextStyle(color = jadePrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
              text = "点击此处或右上角 + 随时添加工作与完成时限",
              style = TextStyle(color = mutedText, fontSize = 11.sp),
            )
          }
        }
      } else {
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
          items(state.tasks, itemId = { it.id.hashCode().toLong() }) { task ->
            val isOverdue = task.deadlineMillis in 1 until now
            val isSoon = !isOverdue && (task.deadlineMillis - now) in 0..(60 * 60 * 1000L)
            val badgeBg = when {
              isOverdue -> urgentBg
              isSoon -> amberBg
              else -> jadeSoft
            }
            val badgeText = when {
              isOverdue -> urgentRed
              isSoon -> amberText
              else -> jadePrimary
            }

            Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
              Row(
                modifier = GlanceModifier
                  .fillMaxWidth()
                  .cornerRadius(12.dp)
                  .background(cardBg)
                  .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier = GlanceModifier
                    .size(28.dp)
                    .clickable(
                      actionRunCallback<ToggleTaskAction>(
                        actionParametersOf(TASK_ID_KEY to task.id)
                      )
                    ),
                  contentAlignment = Alignment.Center,
                ) {
                  Image(
                    provider = ImageProvider(R.drawable.ic_widget_circle),
                    contentDescription = "标记完成",
                    modifier = GlanceModifier.size(20.dp),
                  )
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                Column(
                  modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(openAppIntent))
                ) {
                  Text(
                    text = task.title,
                    style = TextStyle(
                      color = inkText,
                      fontSize = 13.sp,
                      fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                  )
                  Text(
                    text = "${task.category} · ${DeadlineFormatter.formatRemainingLabel(task.deadlineMillis, false, now)}",
                    style = TextStyle(
                      color = if (isOverdue) urgentRed else mutedText,
                      fontSize = 10.sp,
                    ),
                    maxLines = 1,
                  )
                }

                Spacer(modifier = GlanceModifier.width(6.dp))

                Box(
                  modifier = GlanceModifier
                    .cornerRadius(8.dp)
                    .background(badgeBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                  Text(
                    text = DeadlineFormatter.formatShortDeadline(task.deadlineMillis, now),
                    style = TextStyle(
                      color = badgeText,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  companion object {
    val TASK_ID_KEY = ActionParameters.Key<String>("task_id")
  }
}

class ToggleTaskAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    val taskId = parameters[TodayTasksWidget.TASK_ID_KEY] ?: return
    WidgetSnapshotStore.markTaskCompletedOptimistic(context, taskId)
    TodayTasksWidget().updateAll(context)

    val localStore = LocalTasksStore(context)
    localStore.toggleTaskCompletion(taskId = taskId, isCompleted = true)
    val tasks = localStore.getAllOnce()
    WidgetSnapshotStore.saveFromTasks(context = context, allTasks = tasks)
    TodayTasksWidget().updateAll(context)
  }
}

class RefreshWidgetAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    val tasks = LocalTasksStore(context).getAllOnce()
    WidgetSnapshotStore.saveFromTasks(context = context, allTasks = tasks)
    TodayTasksWidget().updateAll(context)
  }
}
