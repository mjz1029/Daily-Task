package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ai.LlmConfig
import com.example.ai.LlmProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LlmSettingsSheetContent(
  initialConfig: LlmConfig,
  onTestConnection: suspend (LlmConfig) -> Result<String>,
  onSaveConfig: (LlmConfig) -> Unit,
  onDismiss: () -> Unit,
  onBack: (() -> Unit)? = null,
) {
  var selectedProvider by remember(initialConfig) { mutableStateOf(initialConfig.provider) }
  var apiKey by remember(initialConfig) { mutableStateOf(initialConfig.apiKey) }
  var baseUrl by remember(initialConfig) { mutableStateOf(initialConfig.baseUrl) }
  var modelName by remember(initialConfig) { mutableStateOf(initialConfig.modelName) }
  var showKey by remember { mutableStateOf(false) }

  var isTesting by remember { mutableStateOf(false) }
  var testResultMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

  val scope = rememberCoroutineScope()

  fun currentDraftConfig(): LlmConfig = LlmConfig(
    provider = selectedProvider,
    apiKey = apiKey,
    baseUrl = baseUrl,
    modelName = modelName,
  )

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
          modifier = Modifier.testTag("llm_settings_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回设置",
          )
        }
      }
      Icon(
        imageVector = Icons.Rounded.AutoAwesome,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = "AI 大模型 API 切换",
        style = MaterialTheme.typography.headlineSmall,
      )
    }

    Text(
      text = "选择你习惯的大模型引擎，用于「智能速记」自动解析工作内容与完成时限。内置 Gemini 无需 API Key 即可开箱使用。",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(text = "模型服务商", style = MaterialTheme.typography.labelLarge)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        LlmProvider.entries.forEach { provider ->
          FilterChip(
            selected = selectedProvider == provider,
            onClick = {
              selectedProvider = provider
              baseUrl = provider.defaultBaseUrl
              modelName = provider.defaultModel
              testResultMessage = null
            },
            label = { Text(provider.shortLabel) },
            leadingIcon = if (selectedProvider == provider) {
              { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
            modifier = Modifier.testTag("llm_provider_${provider.id}"),
          )
        }
      }
    }

    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = selectedProvider.displayName,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = selectedProvider.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (selectedProvider.requiresApiKey) {
          OutlinedTextField(
            value = apiKey,
            onValueChange = {
              apiKey = it
              testResultMessage = null
            },
            label = { Text("API Key *") },
            placeholder = { Text("sk-...") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { showKey = !showKey }) {
                Icon(
                  imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                  contentDescription = if (showKey) "隐藏 API Key" else "显示 API Key",
                )
              }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("llm_api_key_input"),
          )

          OutlinedTextField(
            value = baseUrl,
            onValueChange = {
              baseUrl = it
              testResultMessage = null
            },
            label = { Text("接口地址 (Base URL)") },
            placeholder = { Text(selectedProvider.defaultBaseUrl) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("llm_base_url_input"),
          )

          OutlinedTextField(
            value = modelName,
            onValueChange = {
              modelName = it
              testResultMessage = null
            },
            label = { Text("模型名称 (Model)") },
            placeholder = { Text(selectedProvider.defaultModel) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("llm_model_name_input"),
          )
        }
      }
    }

    if (testResultMessage != null) {
      val (ok, msg) = testResultMessage!!
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (ok) {
          MaterialTheme.colorScheme.primaryContainer
        } else {
          MaterialTheme.colorScheme.errorContainer
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = msg,
          style = MaterialTheme.typography.bodyMedium,
          color = if (ok) {
            MaterialTheme.colorScheme.onPrimaryContainer
          } else {
            MaterialTheme.colorScheme.onErrorContainer
          },
          modifier = Modifier.padding(12.dp),
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedButton(
        onClick = {
          isTesting = true
          testResultMessage = null
          val draft = currentDraftConfig()
          scope.launch {
            val result = onTestConnection(draft)
            isTesting = false
            testResultMessage = result.fold(
              onSuccess = { true to it },
              onFailure = { false to (it.message ?: "连接失败") },
            )
          }
        },
        enabled = !isTesting,
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("test_llm_connection_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        if (isTesting) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(6.dp))
          Text("测试中…")
        } else {
          Icon(imageVector = Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("测试连接")
        }
      }

      Button(
        onClick = {
          onSaveConfig(currentDraftConfig())
          onDismiss()
        },
        enabled = !isTesting && (!selectedProvider.requiresApiKey || apiKey.isNotBlank()),
        modifier = Modifier
          .weight(1.2f)
          .height(48.dp)
          .testTag("save_llm_config_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text("保存并使用")
      }
    }

    Spacer(modifier = Modifier.height(10.dp))
  }
}
