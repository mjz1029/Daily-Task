package com.example.ui.screens

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.LlmConfig
import com.example.data.LocalTaskRecord
import com.example.notifications.TaskReminderScheduler
import com.example.widget.TodayTasksWidgetReceiver

private enum class SettingsPage {
  MAIN,
  DEVICE_TRANSFER,
  LLM_API,
}

@Composable
fun SettingsSheetContent(
  llmConfig: LlmConfig,
  totalLocalTaskCount: Int,
  unfinishedTodayCount: Int,
  overdueCount: Int,
  postureLabel: String,
  onTestLlmConnection: suspend (LlmConfig) -> Result<String>,
  onSaveLlmConfig: (LlmConfig) -> Unit,
  onLoadRecordsForTransfer: suspend () -> List<LocalTaskRecord>,
  onImportRecords: suspend (List<LocalTaskRecord>, Boolean) -> Int,
  onNotifyMessage: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }

  BackHandler(enabled = currentPage != SettingsPage.MAIN) {
    currentPage = SettingsPage.MAIN
  }

  var hasNotificationPermission by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasNotificationPermission = granted
    if (granted) {
      TaskReminderScheduler.showDigestNotification(context, unfinishedTodayCount, overdueCount)
      onNotifyMessage("已开启通知提醒并发送今日待办汇总")
    } else {
      onNotifyMessage("未授予通知权限，可在系统设置中开启截止提醒")
    }
  }

  AnimatedContent(
    targetState = currentPage,
    transitionSpec = {
      fadeIn(tween(160)) togetherWith fadeOut(tween(120))
    },
    label = "settingsPageNavigation",
  ) { page ->
    when (page) {
      SettingsPage.MAIN -> {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Settings,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(26.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
              )
              Text(
                text = "纯单机本地模式 · 当前布局：$postureLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Text(
            text = "数据与智能引擎",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )

          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsEntryCard(
              icon = Icons.Rounded.SwapHoriz,
              title = "换机配对转移",
              subtitle = "6 位配对码无线直传 / 离线加密口令迁移（共 $totalLocalTaskCount 项待办）",
              onClick = { currentPage = SettingsPage.DEVICE_TRANSFER },
              testTag = "settings_entry_device_transfer",
            )

            SettingsEntryCard(
              icon = Icons.Rounded.AutoAwesome,
              title = "AI 大模型 API 切换",
              subtitle = "当前引擎：${llmConfig.badgeLabel} · 切换内置或第三方 API Key",
              onClick = { currentPage = SettingsPage.LLM_API },
              testTag = "settings_entry_llm_api",
            )
          }

          Text(
            text = "桌面小组件与提醒",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )

          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsEntryCard(
              icon = Icons.Rounded.Widgets,
              title = "添加桌面小组件到主屏幕",
              subtitle = "在桌面实时显示今日未完成的工作与完成时限",
              onClick = {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val provider = ComponentName(context, TodayTasksWidgetReceiver::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
                  appWidgetManager.requestPinAppWidget(provider, null, null)
                  onNotifyMessage("请在桌面弹窗中确认添加「今日待办」小组件")
                } else {
                  onNotifyMessage("请在系统桌面长按空白处 -> 添加小组件 -> 选择「今日待办」")
                }
              },
              testTag = "pin_widget_button",
            )

            SettingsEntryCard(
              icon = Icons.Rounded.NotificationsActive,
              title = if (hasNotificationPermission) "立即推送今日未完成汇总提醒" else "开启通知权限并提醒今日工作",
              subtitle = "今日未完成 $unfinishedTodayCount 项 · 已逾期 $overdueCount 项",
              onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                  permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                  TaskReminderScheduler.showDigestNotification(context, unfinishedTodayCount, overdueCount)
                  onNotifyMessage("已推送今日未完成工作提醒通知")
                }
              },
              testTag = "send_digest_notification_button",
            )
          }

          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("close_settings_sheet_button"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Text("完成")
          }

          Spacer(modifier = Modifier.height(10.dp))
        }
      }

      SettingsPage.DEVICE_TRANSFER -> {
        DeviceTransferContent(
          totalLocalTaskCount = totalLocalTaskCount,
          onLoadRecordsForTransfer = onLoadRecordsForTransfer,
          onImportRecords = onImportRecords,
          onNotifyMessage = onNotifyMessage,
          onBack = { currentPage = SettingsPage.MAIN },
        )
      }

      SettingsPage.LLM_API -> {
        LlmSettingsSheetContent(
          initialConfig = llmConfig,
          onTestConnection = onTestLlmConnection,
          onSaveConfig = onSaveLlmConfig,
          onDismiss = { currentPage = SettingsPage.MAIN },
          onBack = { currentPage = SettingsPage.MAIN },
        )
      }
    }
  }
}

@Composable
private fun SettingsEntryCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  testTag: String,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier
      .fillMaxWidth()
      .testTag(testTag),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(42.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp),
          )
        }
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
