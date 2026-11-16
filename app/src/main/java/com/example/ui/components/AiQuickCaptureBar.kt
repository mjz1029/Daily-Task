package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.ai.LlmConfig
import com.example.viewmodel.AiParseUiState

@Composable
fun AiQuickCaptureBar(
  llmConfig: LlmConfig,
  parseState: AiParseUiState,
  compact: Boolean = false,
  onCollapse: (() -> Unit)? = null,
  onParseToFill: (sentence: String, clearInput: () -> Unit) -> Unit,
  onParseAndCreate: (sentence: String, clearInput: () -> Unit) -> Unit,
  onClearError: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var sentence by rememberSaveable { mutableStateOf("") }
  val isParsing = parseState is AiParseUiState.Parsing

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation = 2.dp,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 12.dp),
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
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = "智能速记",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = llmConfig.badgeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (llmConfig.isReadyToUse) {
              MaterialTheme.colorScheme.onSurfaceVariant
            } else {
              MaterialTheme.colorScheme.error
            },
          )

          if (onCollapse != null) {
            IconButton(
              onClick = onCollapse,
              modifier = Modifier
                .size(32.dp)
                .testTag("collapse_ai_quick_bar_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = "收起为按钮",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
      }

      OutlinedTextField(
        value = sentence,
        onValueChange = {
          sentence = it
          onClearError()
        },
        placeholder = {
          Text("一句话记一条或多条，如：上午10点开会，下午5点交周报很急")
        },
        singleLine = true,
        enabled = !isParsing,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
          onDone = {
            if (sentence.isNotBlank() && !isParsing) {
              onParseAndCreate(sentence) { sentence = "" }
            }
          }
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("ai_sentence_input"),
      )

      AnimatedVisibility(visible = parseState is AiParseUiState.Error) {
        val errMsg = (parseState as? AiParseUiState.Error)?.message.orEmpty()
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = errMsg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilledTonalButton(
          onClick = { onParseToFill(sentence) { sentence = "" } },
          enabled = !isParsing && sentence.isNotBlank(),
          modifier = Modifier
            .weight(1f)
            .height(42.dp)
            .testTag("ai_fill_form_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.EditNote,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
          )
          Spacer(modifier = Modifier.width(5.dp))
          Text("填入表单")
        }

        Button(
          onClick = { onParseAndCreate(sentence) { sentence = "" } },
          enabled = !isParsing && sentence.isNotBlank(),
          modifier = Modifier
            .weight(1.15f)
            .height(42.dp)
            .testTag("ai_quick_create_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          if (isParsing) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("识别中…")
          } else {
            Icon(
              imageVector = Icons.Rounded.AutoAwesome,
              contentDescription = null,
              modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text("速记创建")
          }
        }
      }
    }
  }
}
