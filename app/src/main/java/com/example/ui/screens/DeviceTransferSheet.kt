package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhonelinkSetup
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LocalTaskRecord
import com.example.transfer.DevicePairingTransfer
import com.example.transfer.PairingSessionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TransferTab(val label: String) {
  SEND("发送给新设备"),
  RECEIVE("从旧设备接收"),
}

@Composable
fun DeviceTransferContent(
  totalLocalTaskCount: Int,
  onLoadRecordsForTransfer: suspend () -> List<LocalTaskRecord>,
  onImportRecords: suspend (List<LocalTaskRecord>, Boolean) -> Int,
  onNotifyMessage: (String) -> Unit,
  onBack: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var selectedTab by remember { mutableStateOf(TransferTab.SEND) }

  // Send state
  var activeServer by remember { mutableStateOf<DevicePairingTransfer.PairingServer?>(null) }
  var pairingSession by remember { mutableStateOf<PairingSessionInfo?>(null) }
  var transferredCount by remember { mutableIntStateOf(0) }
  var sendError by remember { mutableStateOf<String?>(null) }
  var isGeneratingOfflineToken by remember { mutableStateOf(false) }

  // Receive state
  var pairingCodeInput by remember { mutableStateOf("") }
  var addressOrTokenInput by remember { mutableStateOf("") }
  var replaceExisting by remember { mutableStateOf(false) }
  var isReceiving by remember { mutableStateOf(false) }
  var receiveError by remember { mutableStateOf<String?>(null) }
  var receiveSuccessInfo by remember { mutableStateOf<String?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      activeServer?.stop(context)
      activeServer = null
    }
  }

  fun startNewPairingSession() {
    sendError = null
    transferredCount = 0
    activeServer?.stop(context)
    val code = DevicePairingTransfer.generateSixDigitCode()
    val server = DevicePairingTransfer.PairingServer(
      pairingCode = code,
      recordsProvider = { onLoadRecordsForTransfer() },
      onTransferredCount = { count -> transferredCount = count },
    )
    runCatching {
      server.start(context = context, scope = scope)
    }.onSuccess { info ->
      activeServer = server
      pairingSession = info
    }.onFailure { err ->
      activeServer = null
      pairingSession = null
      sendError = err.localizedMessage ?: "无法启动本机无线发送服务，请尝试使用离线迁移口令"
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (onBack != null) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("device_transfer_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回设置",
          )
        }
      }
      Icon(
        imageVector = Icons.Rounded.SwapHoriz,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(26.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "换机配对转移",
          style = MaterialTheme.typography.headlineSmall,
        )
        Text(
          text = "纯单机本地传输 · 本机共 $totalLocalTaskCount 项待办",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Surface(
      shape = RoundedCornerShape(18.dp),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.PhonelinkSetup,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "6 位配对码无线迁移",
            style = MaterialTheme.typography.titleMedium,
          )
        }

        Text(
          text = "无需任何账号或云端服务器。两台手机在同一 Wi-Fi 下可直接用 6 位配对码直传；跨网络时也可生成加密离线口令复制转移。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          TransferTab.entries.forEachIndexed { index, tab ->
            SegmentedButton(
              selected = selectedTab == tab,
              onClick = {
                selectedTab = tab
                receiveError = null
              },
              shape = SegmentedButtonDefaults.itemShape(
                index = index,
                count = TransferTab.entries.size,
              ),
              icon = {
                Icon(
                  imageVector = if (tab == TransferTab.SEND) Icons.Rounded.Upload else Icons.Rounded.Download,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              },
              modifier = Modifier.testTag("transfer_tab_${tab.name.lowercase()}"),
            ) {
              Text(tab.label, maxLines = 1)
            }
          }
        }

        when (selectedTab) {
          TransferTab.SEND -> {
            val session = pairingSession
            if (session == null) {
              Button(
                onClick = { startNewPairingSession() },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("generate_pairing_code_button"),
                shape = RoundedCornerShape(12.dp),
              ) {
                Icon(imageVector = Icons.Rounded.QrCode2, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("生成 6 位配对码并等待接收")
              }
            } else {
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = "请在新手机输入以下配对码",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                  Text(
                    text = DevicePairingTransfer.formatCodeSpaced(session.pairingCode),
                    style = MaterialTheme.typography.displaySmall.copy(
                      fontFamily = FontFamily.Monospace,
                      fontWeight = FontWeight.Bold,
                      letterSpacing = 4.sp,
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("generated_pairing_code_text"),
                  )
                  if (!session.directAddress.isNullOrBlank()) {
                    Text(
                      text = "局域网直连地址：${session.directAddress}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                  }
                  Text(
                    text = if (transferredCount > 0) {
                      "已成功向新设备发送 $transferredCount 项工作待办"
                    } else {
                      "当前保持页面开启，正在等待新手机连入…"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                OutlinedButton(
                  onClick = { startNewPairingSession() },
                  modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("refresh_pairing_code_button"),
                  shape = RoundedCornerShape(12.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("换一个码")
                }

                FilledTonalButton(
                  onClick = {
                    if (isGeneratingOfflineToken) return@FilledTonalButton
                    isGeneratingOfflineToken = true
                    scope.launch {
                      val encoded = runCatching {
                        val records = onLoadRecordsForTransfer()
                        withContext(Dispatchers.Default) {
                          DevicePairingTransfer.encodeOfflineEncryptedPackage(records, session.pairingCode)
                        }
                      }
                      isGeneratingOfflineToken = false
                      encoded.onSuccess { token ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("今日待办离线迁移口令", token))
                        onNotifyMessage("已复制加密离线口令，到新设备配合配对码 ${session.pairingCode} 粘贴即可导入")
                      }.onFailure { err ->
                        sendError = err.localizedMessage ?: "生成离线口令失败"
                      }
                    }
                  },
                  enabled = !isGeneratingOfflineToken,
                  modifier = Modifier
                    .weight(1.4f)
                    .height(48.dp)
                    .testTag("copy_offline_package_button"),
                  shape = RoundedCornerShape(12.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (isGeneratingOfflineToken) "加密中…" else "复制离线迁移口令")
                }
              }
            }

            if (!sendError.isNullOrBlank()) {
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = sendError.orEmpty(),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(10.dp),
                )
              }
            }
          }

          TransferTab.RECEIVE -> {
            OutlinedTextField(
              value = pairingCodeInput,
              onValueChange = { raw ->
                pairingCodeInput = raw.filter { it.isDigit() }.take(6)
                receiveError = null
              },
              label = { Text("输入旧设备上的 6 位配对码") },
              placeholder = { Text("例如 492817") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("receive_pairing_code_input"),
              shape = RoundedCornerShape(12.dp),
            )

            OutlinedTextField(
              value = addressOrTokenInput,
              onValueChange = {
                addressOrTokenInput = it
                receiveError = null
              },
              label = { Text("可选：局域网地址或 TD1 离线迁移口令") },
              placeholder = { Text("同一 Wi-Fi 可留空自动搜索；或填 192.168.x.x:端口 / TD1:...") },
              maxLines = 3,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("receive_address_or_token_input"),
              shape = RoundedCornerShape(12.dp),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              FilterChip(
                selected = !replaceExisting,
                onClick = { replaceExisting = false },
                label = { Text("合并保留现有") },
                modifier = Modifier.testTag("import_mode_merge_chip"),
              )
              FilterChip(
                selected = replaceExisting,
                onClick = { replaceExisting = true },
                label = { Text("覆盖本机待办") },
                modifier = Modifier.testTag("import_mode_replace_chip"),
              )
            }

            if (!receiveError.isNullOrBlank()) {
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = receiveError.orEmpty(),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(10.dp),
                )
              }
            }

            if (!receiveSuccessInfo.isNullOrBlank()) {
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = receiveSuccessInfo.orEmpty(),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(10.dp),
                )
              }
            }

            Button(
              onClick = {
                receiveError = null
                receiveSuccessInfo = null
                val cleanCode = pairingCodeInput.filter { it.isDigit() }
                if (cleanCode.length != 6) {
                  receiveError = "请输入完整的 6 位数字配对码"
                  return@Button
                }
                isReceiving = true
                scope.launch {
                  val extra = addressOrTokenInput.trim()
                  val result = if (extra.startsWith("TD1:")) {
                    withContext(Dispatchers.Default) {
                      DevicePairingTransfer.decodeOfflineEncryptedPackage(extra, cleanCode)
                    }
                  } else {
                    DevicePairingTransfer.receiveOverLan(
                      context = context,
                      pairingCode = cleanCode,
                      manualAddress = extra.ifBlank { null },
                    )
                  }
                  result.onSuccess { bundle ->
                    val importedCount = onImportRecords(bundle.records, replaceExisting)
                    isReceiving = false
                    receiveSuccessInfo = "接收完成！已导入 $importedCount 项工作待办（共读取 ${bundle.records.size} 项）。"
                    onNotifyMessage("已成功导入 $importedCount 项待办")
                  }.onFailure { err ->
                    isReceiving = false
                    receiveError = err.localizedMessage ?: "接收失败，请核对配对码或网络"
                  }
                }
              },
              enabled = !isReceiving,
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("receive_transfer_button"),
              shape = RoundedCornerShape(12.dp),
            ) {
              if (isReceiving) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("正在校验配对码并接收…")
              } else {
                Icon(imageVector = Icons.Rounded.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("通过配对码接收数据")
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))
  }
}
