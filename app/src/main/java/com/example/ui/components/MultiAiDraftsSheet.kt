package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.ParsedTaskDraft
import com.example.model.TaskPriority
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.DeadlineFormatter

@Composable
fun MultiAiDraftsSheetContent(
  drafts: List<ParsedTaskDraft>,
  isSaving: Boolean,
  onEditSingle: (Int) -> Unit,
  onRemoveDraft: (Int) -> Unit,
  onConfirmAll: () -> Unit,
  onDismiss: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.AutoAwesome,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "已识别 ${drafts.size} 项工作待办",
          style = MaterialTheme.typography.headlineSmall,
        )
        Text(
          text = "可单条微调或移除，确认后一键全部加入今日待办",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 360.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      itemsIndexed(drafts) { index, draft ->
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = draft.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
              )
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = DeadlineFormatter.formatDeadlineFull(draft.deadlineMillis),
                  style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = draft.category,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (draft.priority == TaskPriority.HIGH) {
                  Text(
                    text = "紧急",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                  )
                }
              }
              if (draft.notes.isNotBlank()) {
                Text(
                  text = draft.notes,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            IconButton(
              onClick = { onEditSingle(index) },
              modifier = Modifier.testTag("multi_draft_edit_$index"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = "单独编辑此待办",
                tint = MaterialTheme.colorScheme.primary,
              )
            }

            IconButton(
              onClick = { onRemoveDraft(index) },
              modifier = Modifier.testTag("multi_draft_remove_$index"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "移除此待办",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedButton(
        onClick = onDismiss,
        enabled = !isSaving,
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("multi_draft_cancel_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text("取消")
      }

      Button(
        onClick = onConfirmAll,
        enabled = !isSaving && drafts.isNotEmpty(),
        modifier = Modifier
          .weight(1.5f)
          .height(48.dp)
          .testTag("multi_draft_confirm_all_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        if (isSaving) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
        } else {
          Icon(
            imageVector = Icons.Rounded.PlaylistAddCheck,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("一键全部添加 (${drafts.size} 项)")
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))
  }
}
