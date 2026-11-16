package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.ParsedTaskDraft
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.DeadlineFormatter
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorContent(
  editingTask: TaskItem?,
  pendingAiDraft: ParsedTaskDraft? = null,
  onConsumeAiDraft: () -> Unit = {},
  llmBadgeLabel: String = "内置 Gemini",
  isAiParsing: Boolean = false,
  onAiParseFill: (String) -> Unit = {},
  isSaving: Boolean,
  showCloseButton: Boolean,
  onClose: () -> Unit,
  onSaveNew: (
    title: String,
    notes: String,
    deadlineMillis: Long,
    priority: TaskPriority,
    category: String,
    remindMinutesBefore: Long,
  ) -> Unit,
  onUpdateExisting: (
    existing: TaskItem,
    title: String,
    notes: String,
    deadlineMillis: Long,
    priority: TaskPriority,
    category: String,
    remindMinutesBefore: Long,
  ) -> Unit,
  onDelete: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val defaultDeadline = remember(editingTask?.id) {
    editingTask?.deadlineMillis?.takeIf { it > 0L }
      ?: (System.currentTimeMillis() + 60 * 60 * 1000L)
  }

  var title by remember(editingTask?.id) { mutableStateOf(editingTask?.title ?: "") }
  var notes by remember(editingTask?.id) { mutableStateOf(editingTask?.notes ?: "") }
  var deadlineMillis by remember(editingTask?.id) { mutableLongStateOf(defaultDeadline) }
  var priority by remember(editingTask?.id) {
    mutableStateOf(editingTask?.priorityLevel ?: TaskPriority.MEDIUM)
  }
  var category by remember(editingTask?.id) { mutableStateOf(editingTask?.category ?: "工作") }
  var remindMinutes by remember(editingTask?.id) {
    mutableLongStateOf(editingTask?.remindMinutesBefore ?: 15L)
  }

  var aiPromptText by rememberSaveable(editingTask?.id) { mutableStateOf("") }
  var showDatePicker by remember { mutableStateOf(false) }
  var showTimePicker by remember { mutableStateOf(false) }

  // Apply AI parsed draft directly into the editor fields when ready
  LaunchedEffect(pendingAiDraft) {
    val draft = pendingAiDraft ?: return@LaunchedEffect
    title = draft.title
    notes = draft.notes
    deadlineMillis = draft.deadlineMillis
    priority = draft.priority
    category = draft.category
    remindMinutes = draft.remindMinutesBefore
    onConsumeAiDraft()
  }

  val categories = remember { listOf("工作", "会议", "沟通", "开发", "个人") }
  val remindOptions = remember { listOf(0L to "准点提醒", 5L to "提前5分", 15L to "提前15分", 30L to "提前30分", 60L to "提前1小时") }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Header row
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (editingTask == null) "随时添加今日工作" else "编辑工作与时限",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "保存后自动刷新桌面小组件与截止提醒",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (showCloseButton) {
        IconButton(onClick = onClose, modifier = Modifier.testTag("close_editor_button")) {
          Icon(imageVector = Icons.Rounded.Close, contentDescription = "关闭编辑面板")
        }
      }
    }

    // Inline AI One-Sentence Fill inside the Editor (for creating new tasks)
    if (editingTask == null) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "智能速记填表",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Text(
              text = llmBadgeLabel,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = aiPromptText,
              onValueChange = { aiPromptText = it },
              placeholder = { Text("例如：今晚8点前完成合同审阅") },
              singleLine = true,
              enabled = !isAiParsing,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("editor_ai_prompt_input"),
            )
            FilledTonalButton(
              onClick = { onAiParseFill(aiPromptText) },
              enabled = !isAiParsing && aiPromptText.isNotBlank(),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .height(48.dp)
                .testTag("editor_ai_fill_button"),
            ) {
              if (isAiParsing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
              } else {
                Text("填充")
              }
            }
          }
        }
      }
    }

    // Work title
    OutlinedTextField(
      value = title,
      onValueChange = { if (it.length <= 120) title = it },
      label = { Text("工作内容 *") },
      placeholder = { Text("例如：提交 Q4 进度报告、联系客户确认需求") },
      singleLine = true,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("task_title_input"),
      shape = RoundedCornerShape(14.dp),
    )

    // Notes
    OutlinedTextField(
      value = notes,
      onValueChange = { if (it.length <= 500) notes = it },
      label = { Text("补充说明 / 交付标准（可选）") },
      placeholder = { Text("记录关键要点、链接或交付细节") },
      minLines = 2,
      maxLines = 4,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("task_notes_input"),
      shape = RoundedCornerShape(14.dp),
    )

    // Deadline section
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Schedule,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "完成时限",
              style = MaterialTheme.typography.titleMedium,
            )
          }
          Text(
            text = DeadlineFormatter.formatDeadlineFull(deadlineMillis),
            style = MaterialTheme.typography.labelLarge.copy(
              fontFamily = JetBrainsMonoFamily,
              fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.primary,
          )
        }

        // Quick preset chips
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val now = System.currentTimeMillis()
          val presets = listOf(
            "30分钟后" to (now + 30 * 60_000L),
            "1小时后" to (now + 60 * 60_000L),
            "今天 12:00" to DeadlineFormatter.todayAt(12, 0, now),
            "今天 18:00" to DeadlineFormatter.todayAt(18, 0, now),
            "今天 21:00" to DeadlineFormatter.todayAt(21, 0, now),
            "明天 09:00" to DeadlineFormatter.tomorrowAt(9, 0, now),
          )
          presets.forEach { (label, targetMillis) ->
            val isSelected = kotlin.math.abs(deadlineMillis - targetMillis) < 90_000L
            FilterChip(
              selected = isSelected,
              onClick = { deadlineMillis = targetMillis },
              label = { Text(label) },
            )
          }
        }

        // Custom date & time buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier
              .weight(1f)
              .testTag("pick_date_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.CalendarToday,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("选择日期")
          }
          OutlinedButton(
            onClick = { showTimePicker = true },
            modifier = Modifier
              .weight(1f)
              .testTag("pick_time_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Schedule,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("精确时刻")
          }
        }
      }
    }

    // Priority
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(text = "紧急程度", style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TaskPriority.entries.forEach { level ->
          FilterChip(
            selected = priority == level,
            onClick = { priority = level },
            label = { Text(level.labelZh) },
            leadingIcon = if (priority == level) {
              { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
          )
        }
      }
    }

    // Category
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(text = "工作分类", style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { item ->
          FilterChip(
            selected = category == item,
            onClick = { category = item },
            label = { Text(item) },
          )
        }
      }
    }

    // Reminder lead time
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.NotificationsActive,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp),
        )
        Text(text = "截止提醒时机", style = MaterialTheme.typography.labelLarge)
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        remindOptions.forEach { (mins, label) ->
          FilterChip(
            selected = remindMinutes == mins,
            onClick = { remindMinutes = mins },
            label = { Text(label) },
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Action Buttons
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (editingTask != null) {
        OutlinedButton(
          onClick = { onDelete(editingTask.id) },
          enabled = !isSaving,
          modifier = Modifier.testTag("delete_task_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = "删除任务",
            tint = MaterialTheme.colorScheme.error,
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("删除", color = MaterialTheme.colorScheme.error)
        }
      }

      Button(
        onClick = {
          if (editingTask == null) {
            onSaveNew(title, notes, deadlineMillis, priority, category, remindMinutes)
          } else {
            onUpdateExisting(editingTask, title, notes, deadlineMillis, priority, category, remindMinutes)
          }
        },
        enabled = !isSaving && title.isNotBlank(),
        modifier = Modifier
          .weight(1f)
          .height(50.dp)
          .testTag("save_task_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        if (isSaving) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
        } else {
          Text(if (editingTask == null) "添加到今日待办" else "保存修改")
        }
      }
    }
  }

  // Material 3 DatePickerDialog
  if (showDatePicker) {
    val calLocal = remember(deadlineMillis) {
      Calendar.getInstance().apply { timeInMillis = deadlineMillis }
    }
    val initialUtcMillis = remember(deadlineMillis) {
      val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
      utcCal.set(
        calLocal.get(Calendar.YEAR),
        calLocal.get(Calendar.MONTH),
        calLocal.get(Calendar.DAY_OF_MONTH),
        0,
        0,
        0,
      )
      utcCal.set(Calendar.MILLISECOND, 0)
      utcCal.timeInMillis
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { selectedUtc ->
              val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = selectedUtc
              }
              val updated = Calendar.getInstance().apply {
                timeInMillis = deadlineMillis
                set(Calendar.YEAR, utc.get(Calendar.YEAR))
                set(Calendar.MONTH, utc.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
              }
              deadlineMillis = updated.timeInMillis
            }
            showDatePicker = false
          }
        ) {
          Text("确定")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("取消") }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }

  // Material 3 TimePicker Dialog
  if (showTimePicker) {
    val currentCal = remember(deadlineMillis) {
      Calendar.getInstance().apply { timeInMillis = deadlineMillis }
    }
    val timePickerState = rememberTimePickerState(
      initialHour = currentCal.get(Calendar.HOUR_OF_DAY),
      initialMinute = currentCal.get(Calendar.MINUTE),
      is24Hour = true,
    )
    AlertDialog(
      onDismissRequest = { showTimePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            val updated = Calendar.getInstance().apply {
              timeInMillis = deadlineMillis
              set(Calendar.HOUR_OF_DAY, timePickerState.hour)
              set(Calendar.MINUTE, timePickerState.minute)
              set(Calendar.SECOND, 0)
              set(Calendar.MILLISECOND, 0)
            }
            deadlineMillis = updated.timeInMillis
            showTimePicker = false
          }
        ) {
          Text("确定")
        }
      },
      dismissButton = {
        TextButton(onClick = { showTimePicker = false }) { Text("取消") }
      },
      title = { Text("设置完成时刻") },
      text = { TimePicker(state = timePickerState) },
    )
  }
}
