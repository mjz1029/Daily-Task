package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ai.configureAppCheck
import com.example.ui.screens.TasksWorkspaceScreen
import com.example.ui.theme.TodayTasksTheme

class MainActivity : ComponentActivity() {
  private var openAddTaskTrigger by mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    enableEdgeToEdge()
    handleIntentExtras(intent)

    setContent {
      TodayTasksTheme {
        TasksWorkspaceScreen(
          openAddTaskOnLaunch = openAddTaskTrigger,
          onConsumedOpenAddTask = { openAddTaskTrigger = false },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntentExtras(intent)
  }

  private fun handleIntentExtras(intent: Intent?) {
    if (intent?.getBooleanExtra(EXTRA_OPEN_ADD_TASK, false) == true) {
      openAddTaskTrigger = true
      intent.removeExtra(EXTRA_OPEN_ADD_TASK)
    }
  }

  companion object {
    const val EXTRA_OPEN_ADD_TASK = "extra_open_add_task"
  }
}
