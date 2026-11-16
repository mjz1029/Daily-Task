package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTimeFilled
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.TaskItem
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.DeadlineFormatter

@Composable
fun TodayFocusHeader(
  unfinishedTodayCount: Int,
  completedTodayCount: Int,
  overdueCount: Int,
  nextUrgentTask: TaskItem?,
  nowMillis: Long,
  compactMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val totalToday = (unfinishedTodayCount + completedTodayCount).coerceAtLeast(1)
  val rawProgress = if (unfinishedTodayCount == 0 && completedTodayCount == 0) {
    1f
  } else {
    completedTodayCount.toFloat() / totalToday.toFloat()
  }
  val animatedProgress by animateFloatAsState(
    targetValue = rawProgress.coerceIn(0f, 1f),
    animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f),
    label = "todayProgress",
  )

  val ringTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
  val ringProgressColor = if (overdueCount > 0) {
    MaterialTheme.colorScheme.tertiary
  } else {
    MaterialTheme.colorScheme.primary
  }

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    tonalElevation = 2.dp,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 18.dp, vertical = if (compactMode) 14.dp else 18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Custom Canvas Progress Ring
        Box(
          modifier = Modifier.size(if (compactMode) 60.dp else 72.dp),
          contentAlignment = Alignment.Center,
        ) {
          Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = 7.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
              color = ringTrackColor,
              startAngle = -90f,
              sweepAngle = 360f,
              useCenter = false,
              topLeft = Offset(inset, inset),
              size = arcSize,
              style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            drawArc(
              color = ringProgressColor,
              startAngle = -90f,
              sweepAngle = 360f * animatedProgress,
              useCenter = false,
              topLeft = Offset(inset, inset),
              size = arcSize,
              style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "$unfinishedTodayCount",
              style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
              ),
              color = if (overdueCount > 0) {
                MaterialTheme.colorScheme.error
              } else {
                MaterialTheme.colorScheme.primary
              },
            )
            Text(
              text = "待办",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = when {
              unfinishedTodayCount == 0 && completedTodayCount == 0 -> "今天工作暂未安排"
              unfinishedTodayCount == 0 -> "今天的工作全部完成！"
              else -> "今天还有 $unfinishedTodayCount 项工作未完成"
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Text(
                text = "已完成 $completedTodayCount 项",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
            if (overdueCount > 0) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = "逾期提醒",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(13.dp),
                  )
                  Text(
                    text = "$overdueCount 项已逾期",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                  )
                }
              }
            }
          }
        }
      }

      if (nextUrgentTask != null) {
        val isOverdue = nextUrgentTask.isOverdue(nowMillis)
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = if (isOverdue) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
          } else {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.AccessTimeFilled,
              contentDescription = null,
              tint = if (isOverdue) {
                MaterialTheme.colorScheme.error
              } else {
                MaterialTheme.colorScheme.tertiary
              },
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "下一截止：${nextUrgentTask.title}",
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Text(
              text = DeadlineFormatter.formatRemainingLabel(
                deadlineMillis = nextUrgentTask.deadlineMillis,
                isCompleted = false,
                nowMillis = nowMillis,
              ),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = if (isOverdue) {
                MaterialTheme.colorScheme.error
              } else {
                MaterialTheme.colorScheme.tertiary
              },
            )
          }
        }
      }
    }
  }
}
