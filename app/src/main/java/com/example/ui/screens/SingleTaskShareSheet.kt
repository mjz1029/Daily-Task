package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.LocalTaskRecord
import com.example.model.TaskItem
import com.example.transfer.DevicePairingTransfer
import com.example.transfer.PairingSessionInfo
import com.example.transfer.QrCodeHelper
import com.example.transfer.SingleTaskShareCodec
import com.example.util.DeadlineFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

sealed interface SingleTaskShareMode {
  data class Share(val task: TaskItem) : SingleTaskShareMode
  data object Receive : SingleTaskShareMode
}

private enum class ReceiveSingleTab(val label: String) {
  SCAN("扫码接收"),
  CODE("填码接收"),
}

@Composable
fun SingleTaskShareSheetContent(
  mode: SingleTaskShareMode,
  onImportTaskRecords: suspend (List<LocalTaskRecord>) -> Int,
  onNotifyMessage: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  when (mode) {
    is SingleTaskShareMode.Share -> ShareSingleTaskContent(
      task = mode.task,
      onNotifyMessage = onNotifyMessage,
      onDismiss = onDismiss,
    )

    SingleTaskShareMode.Receive -> ReceiveSingleTaskContent(
      onImportTaskRecords = onImportTaskRecords,
      onNotifyMessage = onNotifyMessage,
      onDismiss = onDismiss,
    )
  }
}

@Composable
private fun ShareSingleTaskContent(
  task: TaskItem,
  onNotifyMessage: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val shareToken = remember(task.id, task.updatedAtMillis) {
    SingleTaskShareCodec.encodeToken(task)
  }
  var qrBitmap by remember(shareToken) { mutableStateOf<Bitmap?>(null) }
  var pairingSession by remember { mutableStateOf<PairingSessionInfo?>(null) }
  var transferredCount by remember { mutableIntStateOf(0) }

  LaunchedEffect(shareToken) {
    qrBitmap = withContext(Dispatchers.Default) {
      QrCodeHelper.generateQrBitmap(shareToken, sizePx = 560)
    }
  }

  DisposableEffect(task.id) {
    val code = DevicePairingTransfer.generateSixDigitCode()
    val server = DevicePairingTransfer.PairingServer(
      pairingCode = code,
      recordsProvider = { listOf(SingleTaskShareCodec.fromTaskItem(task)) },
      onTransferredCount = { count -> transferredCount = count },
    )
    val info = runCatching { server.start(context = context, scope = scope) }.getOrNull()
    pairingSession = info
    onDispose {
      server.stop(context)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.QrCode2,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "分享单个待办",
          style = MaterialTheme.typography.headlineSmall,
        )
        Text(
          text = "对方可用本应用扫码，或输入下方 6 位分享码接收",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = task.title,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
          text = "截止：${DeadlineFormatter.formatDeadlineFull(task.deadlineMillis)} · ${task.category}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }

    // QR Code card
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = Color.White,
      tonalElevation = 2.dp,
      modifier = Modifier.size(220.dp),
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(14.dp),
        contentAlignment = Alignment.Center,
      ) {
        val bmp = qrBitmap
        if (bmp != null) {
          Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "待办分享二维码",
            modifier = Modifier
              .fillMaxSize()
              .testTag("single_task_qr_image"),
          )
        } else {
          CircularProgressIndicator()
        }
      }
    }

    // 6-digit Wi-Fi share code card
    val session = pairingSession
    if (session != null) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "同 Wi-Fi 6 位分享码",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Text(
            text = DevicePairingTransfer.formatCodeSpaced(session.pairingCode),
            style = MaterialTheme.typography.headlineMedium.copy(
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              letterSpacing = 3.sp,
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.testTag("single_task_share_code_text"),
          )
          if (!session.directAddress.isNullOrBlank()) {
            Text(
              text = "直连地址：${session.directAddress}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
          }
          if (transferredCount > 0) {
            Text(
              text = "对方已成功接收此待办",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      FilledTonalButton(
        onClick = {
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
          clipboard?.setPrimaryClip(ClipData.newPlainText("待办分享口令", shareToken))
          onNotifyMessage("已复制待办分享口令，对方可粘贴接收")
        },
        modifier = Modifier
          .weight(1.3f)
          .height(48.dp)
          .testTag("copy_single_task_token_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("复制口令分享")
      }

      OutlinedButton(
        onClick = onDismiss,
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("close_single_share_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text("关闭")
      }
    }

    Spacer(modifier = Modifier.height(8.dp))
  }
}

@Composable
private fun ReceiveSingleTaskContent(
  onImportTaskRecords: suspend (List<LocalTaskRecord>) -> Int,
  onNotifyMessage: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var selectedTab by remember { mutableStateOf(ReceiveSingleTab.SCAN) }
  var codeOrTokenInput by remember { mutableStateOf("") }
  var manualAddressInput by remember { mutableStateOf("") }
  var isImporting by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasCameraPermission = granted
    if (!granted) {
      errorMsg = "未获得相机权限，可选择相册二维码图片或切换为「填码接收」"
    } else {
      errorMsg = null
    }
  }

  fun importDecodedToken(rawQrText: String) {
    if (isImporting) return
    isImporting = true
    errorMsg = null
    scope.launch {
      val decoded = SingleTaskShareCodec.decodeToken(rawQrText)
      decoded.onSuccess { payload ->
        val record = payload.toNewLocalRecord()
        onImportTaskRecords(listOf(record))
        isImporting = false
        onNotifyMessage("已扫码接收待办「${payload.title}」")
        onDismiss()
      }.onFailure { err ->
        isImporting = false
        errorMsg = err.localizedMessage ?: "二维码内容不是有效的任务分享码"
      }
    }
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      val qrText = withContext(Dispatchers.IO) {
        runCatching {
          val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
              decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
          } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
          }
          QrCodeHelper.decodeQrFromBitmap(bmp)
        }.getOrNull()
      }
      if (qrText.isNullOrBlank()) {
        errorMsg = "未在所选图片中识别到有效二维码"
      } else {
        importDecodedToken(qrText)
      }
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
      Icon(
        imageVector = Icons.Rounded.QrCodeScanner,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "扫码或填码接收待办",
          style = MaterialTheme.typography.headlineSmall,
        )
        Text(
          text = "扫描对方的待办分享二维码，或输入 6 位分享码 / 粘贴 TT1 口令",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      ReceiveSingleTab.entries.forEachIndexed { index, tab ->
        SegmentedButton(
          selected = selectedTab == tab,
          onClick = {
            selectedTab = tab
            errorMsg = null
          },
          shape = SegmentedButtonDefaults.itemShape(
            index = index,
            count = ReceiveSingleTab.entries.size,
          ),
          icon = {
            Icon(
              imageVector = if (tab == ReceiveSingleTab.SCAN) Icons.Rounded.CameraAlt else Icons.Rounded.Keyboard,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          },
          modifier = Modifier.testTag("single_receive_tab_${tab.name.lowercase()}"),
        ) {
          Text(tab.label)
        }
      }
    }

    when (selectedTab) {
      ReceiveSingleTab.SCAN -> {
        if (hasCameraPermission) {
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.Black,
            modifier = Modifier
              .fillMaxWidth()
              .height(250.dp)
              .clip(RoundedCornerShape(18.dp)),
          ) {
            QrCameraScannerView(
              onQrDetected = { raw -> importDecodedToken(raw) },
              modifier = Modifier.fillMaxSize(),
            )
          }
          Text(
            text = "请将取景框对准对方屏幕上的「待办分享二维码」",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
              )
              Text(
                text = "需要相机权限以实时扫描分享二维码",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
              )
              Button(
                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier
                  .height(48.dp)
                  .testTag("grant_camera_scan_button"),
                shape = RoundedCornerShape(12.dp),
              ) {
                Text("开启相机扫码")
              }
            }
          }
        }

        FilledTonalButton(
          onClick = {
            errorMsg = null
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("pick_qr_image_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(imageVector = Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("从相册选择二维码图片")
        }
      }

      ReceiveSingleTab.CODE -> {
        OutlinedTextField(
          value = codeOrTokenInput,
          onValueChange = {
            codeOrTokenInput = it
            errorMsg = null
          },
          label = { Text("输入 6 位分享码或粘贴 TT1 口令 *") },
          placeholder = { Text("例如 492817 或 TT1:...") },
          maxLines = 3,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("single_receive_code_input"),
        )

        val trimmedInput = codeOrTokenInput.trim()
        val looksLikeSixDigitCode = trimmedInput.filter { it.isDigit() }.length == 6 &&
          !trimmedInput.startsWith(SingleTaskShareCodec.TOKEN_PREFIX)

        if (looksLikeSixDigitCode) {
          OutlinedTextField(
            value = manualAddressInput,
            onValueChange = { manualAddressInput = it },
            label = { Text("可选：对方直连地址（同一 Wi-Fi 可留空）") },
            placeholder = { Text("例如 192.168.1.8:49321") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("single_receive_address_input"),
          )
        }

        Button(
          onClick = {
            errorMsg = null
            val raw = codeOrTokenInput.trim()
            if (raw.isBlank()) {
              errorMsg = "请输入 6 位分享码或粘贴分享口令"
              return@Button
            }
            if (raw.startsWith(SingleTaskShareCodec.TOKEN_PREFIX) || raw.startsWith("{")) {
              importDecodedToken(raw)
              return@Button
            }
            val cleanCode = raw.filter { it.isDigit() }
            if (cleanCode.length != 6) {
              errorMsg = "请输入完整的 6 位数字分享码，或以 TT1: 开头的分享口令"
              return@Button
            }
            isImporting = true
            scope.launch {
              val res = DevicePairingTransfer.receiveOverLan(
                context = context,
                pairingCode = cleanCode,
                manualAddress = manualAddressInput.trim().ifBlank { null },
              )
              res.onSuccess { bundle ->
                val count = onImportTaskRecords(bundle.records)
                isImporting = false
                onNotifyMessage("已接收 $count 项共享待办")
                onDismiss()
              }.onFailure { err ->
                isImporting = false
                errorMsg = err.localizedMessage ?: "接收失败，请核对 6 位分享码与 Wi-Fi"
              }
            }
          },
          enabled = !isImporting,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("import_shared_task_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          if (isImporting) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("正在接收…")
          } else {
            Icon(imageVector = Icons.Rounded.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("识别并添加到今日待办")
          }
        }
      }
    }

    if (!errorMsg.isNullOrBlank()) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = errorMsg.orEmpty(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.padding(10.dp),
        )
      }
    }

    OutlinedButton(
      onClick = onDismiss,
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("close_single_receive_button"),
      shape = RoundedCornerShape(14.dp),
    ) {
      Text("取消")
    }

    Spacer(modifier = Modifier.height(8.dp))
  }
}

@Composable
private fun QrCameraScannerView(
  onQrDetected: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val previewView = remember { PreviewView(context) }
  val detectedOnce = remember { AtomicBoolean(false) }
  val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

  DisposableEffect(lifecycleOwner) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    var providerRef: ProcessCameraProvider? = null
    val listener = Runnable {
      runCatching {
        val provider = cameraProviderFuture.get()
        providerRef = provider
        val preview = Preview.Builder().build().also {
          it.surfaceProvider = previewView.surfaceProvider
        }
        val analysis = ImageAnalysis.Builder()
          .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
          .build()
          .also { imageAnalysis ->
            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
              try {
                if (!detectedOnce.get()) {
                  val yPlaneBuffer = imageProxy.planes.firstOrNull()?.buffer
                  if (yPlaneBuffer != null) {
                    val bytes = ByteArray(yPlaneBuffer.remaining())
                    yPlaneBuffer.get(bytes)
                    val rowStride = imageProxy.planes[0].rowStride
                    val decoded = QrCodeHelper.decodeQrFromYuvFrame(
                      yPlane = bytes,
                      width = imageProxy.width,
                      height = imageProxy.height,
                      rowStride = rowStride,
                    )
                    if (!decoded.isNullOrBlank() && detectedOnce.compareAndSet(false, true)) {
                      ContextCompat.getMainExecutor(context).execute {
                        onQrDetected(decoded)
                      }
                    }
                  }
                }
              } finally {
                imageProxy.close()
              }
            }
          }

        provider.unbindAll()
        provider.bindToLifecycle(
          lifecycleOwner,
          CameraSelector.DEFAULT_BACK_CAMERA,
          preview,
          analysis,
        )
      }
    }
    cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

    onDispose {
      runCatching { providerRef?.unbindAll() }
      analysisExecutor.shutdown()
    }
  }

  AndroidView(
    factory = { previewView },
    modifier = modifier.background(Color.Black),
  )
}
