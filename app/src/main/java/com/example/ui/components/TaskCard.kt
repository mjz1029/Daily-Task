package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.util.DeadlineFormatter

@Composable
fun TaskCard(
  task: TaskItem,
  isSelected: Boolean,
  nowMillis: Long,
  onToggleComplete: () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isOverdue = task.isOverdue(nowMillis)
  val isSoon = !task.isCompleted && !isOverdue && (task.deadlineMillis - nowMillis) in 0..(60 * 60 * 1000L)

  val priorityColor = when {
    task.isCompleted -> MaterialTheme.colorScheme.outlineVariant
    isOverdue || task.priorityLevel == TaskPriority.HIGH -> MaterialTheme.colorScheme.error
    isSoon || task.priorityLevel == TaskPriority.MEDIUM -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
  }

  val containerColor by animateColorAsState(
    targetValue = when {
      isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
      task.isCompleted -> MaterialTheme.colorScheme.surfaceContainerLow
      else -> MaterialTheme.colorScheme.surfaceContainerLowest
    },
    label = "cardContainerColor",
  )

  Surface(
    onClick = onClick,
    modifier = modifier
      .fillMaxWidth()
      .testTag("task_card_${task.id}"),
    shape = RoundedCornerShape(18.dp),
    color = containerColor,
    border = if (isSelected) {
      BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else if (isOverdue) {
      BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
    } else {
      BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    },
    tonalElevation = if (task.isCompleted) 0.dp else 1.dp,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 6.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Priority stripe
      Box(
        modifier = Modifier
          .width(4.dp)
          .height(44.dp)
          .clip(CircleShape)
          .background(priorityColor),
      )

      IconButton(
        onClick = onToggleComplete,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("toggle_task_${task.id}"),
      ) {
        Icon(
          imageVector = if (task.isCompleted) {
            Icons.Rounded.CheckCircle
          } else {
            Icons.Rounded.RadioButtonUnchecked
          },
          contentDescription = if (task.isCompleted) "标记为未完成" else "标记为已完成",
          tint = if (task.isCompleted) {
            MaterialTheme.colorScheme.primary
          } else {
            priorityColor
          },
          modifier = Modifier.size(26.dp),
        )
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = task.title,
          style = MaterialTheme.typography.titleMedium.copy(
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
          ),
          color = if (task.isCompleted) {
            MaterialTheme.colorScheme.onSurfaceVariant
          } else {
            MaterialTheme.colorScheme.onSurface
          },
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        if (task.notes.isNotBlank()) {
          Text(
            text = task.notes,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
          ) {
            Text(
              text = task.category,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }

          if (!task.isCompleted && task.remindMinutesBefore > 0L) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = "已设置到期前提醒",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = "提前${task.remindMinutesBefore}分",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      // Deadline clock + remaining time badge
      Column(horizontalAlignment = Alignment.End) {
        val badgeContainer = when {
          task.isCompleted -> MaterialTheme.colorScheme.surfaceContainerHigh
          isOverdue -> MaterialTheme.colorScheme.errorContainer
          isSoon -> MaterialTheme.colorScheme.tertiaryContainer
          else -> MaterialTheme.colorScheme.primaryContainer
        }
        val badgeTextColor = when {
          task.isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant
          isOverdue -> MaterialTheme.colorScheme.onErrorContainer
          isSoon -> MaterialTheme.colorScheme.onTertiaryContainer
          else -> MaterialTheme.colorScheme.onPrimaryContainer
        }

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = badgeContainer,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Schedule,
              contentDescription = null,
              tint = badgeTextColor,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = DeadlineFormatter.formatShortDeadline(task.deadlineMillis, nowMillis),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = badgeTextColor,
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = DeadlineFormatter.formatRemainingLabel(
            deadlineMillis = task.deadlineMillis,
            isCompleted = task.isCompleted,
            nowMillis = nowMillis,
          ),
          style = MaterialTheme.typography.labelSmall,
          color = when {
            task.isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant
            isOverdue -> MaterialTheme.colorScheme.error
            isSoon -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          },
        )
      }
    }
  }
}
