package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ai.FoldableDisplayPreference

@Composable
fun FoldableLayoutModeSelector(
  postureBadgeLabel: String,
  isExpandedFoldable: Boolean,
  currentPreference: FoldableDisplayPreference,
  onSelectPreference: (FoldableDisplayPreference) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }

  Box(modifier = modifier) {
    Surface(
      onClick = { if (isExpandedFoldable) expanded = true },
      shape = RoundedCornerShape(8.dp),
      color = MaterialTheme.colorScheme.primaryContainer,
      modifier = Modifier.testTag("foldable_layout_selector_badge"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (isExpandedFoldable) {
          Icon(
            imageVector = when (currentPreference) {
              FoldableDisplayPreference.FULLSCREEN -> Icons.Rounded.Fullscreen
              FoldableDisplayPreference.LEFT_RIGHT_TWO_PANE -> Icons.Rounded.ViewSidebar
              else -> Icons.Rounded.Splitscreen
            },
            contentDescription = "切换折叠屏展开显示方式",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(14.dp),
          )
        }
        Text(
          text = if (isExpandedFoldable) "$postureBadgeLabel ▾" else postureBadgeLabel,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
    }

    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
    ) {
      FoldableDisplayPreference.entries.forEach { pref ->
        val isSelected = pref == currentPreference
        DropdownMenuItem(
          text = {
            Column {
              Text(
                text = pref.labelZh,
                style = MaterialTheme.typography.titleSmall,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = pref.subtitleZh,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          },
          leadingIcon = {
            Icon(
              imageVector = when (pref) {
                FoldableDisplayPreference.AUTO -> Icons.Rounded.AutoAwesomeMotion
                FoldableDisplayPreference.TOP_BOTTOM_SPLIT -> Icons.Rounded.Splitscreen
                FoldableDisplayPreference.FULLSCREEN -> Icons.Rounded.Fullscreen
                FoldableDisplayPreference.LEFT_RIGHT_TWO_PANE -> Icons.Rounded.ViewSidebar
              },
              contentDescription = null,
              tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          trailingIcon = if (isSelected) {
            {
              Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "当前模式",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          } else null,
          onClick = {
            expanded = false
            onSelectPreference(pref)
          },
          modifier = Modifier.testTag("layout_option_${pref.id}"),
        )
      }
    }
  }
}
