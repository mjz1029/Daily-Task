package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.TaskPriority
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalTasksStoreTest {

  private lateinit var store: LocalTasksStore

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    store = LocalTasksStore(context)
    runBlocking { store.clearAll() }
  }

  @After
  fun tearDown() {
    runBlocking { store.clearAll() }
  }

  @Test
  fun createUpdateToggleDelete_worksWithoutAuthentication() = runBlocking {
    val deadline = System.currentTimeMillis() + 3_600_000L
    val id = store.createTask(
      title = "Z Fold 8 适配评审",
      notes = "检查展开态分栏宽度",
      deadlineMillis = deadline,
      priority = TaskPriority.HIGH,
      category = "产品",
      remindMinutesBefore = 30L,
    )

    val initial = store.getAllOnce()
    assertEquals(1, initial.size)
    assertEquals(id, initial.first().id)
    assertEquals("Z Fold 8 适配评审", initial.first().title)
    assertFalse(initial.first().isCompleted)

    store.toggleTaskCompletion(id, true)
    val toggled = store.getAllOnce().first()
    assertTrue(toggled.isCompleted)
    assertTrue(toggled.completedAtMillis > 0L)

    store.updateTask(toggled.copy(title = "Z Fold 8 最终评审", priority = TaskPriority.MEDIUM.wireValue))
    val updated = store.getAllOnce().first()
    assertEquals("Z Fold 8 最终评审", updated.title)
    assertEquals(TaskPriority.MEDIUM.wireValue, updated.priority)

    store.deleteTask(id)
    assertTrue(store.getAllOnce().isEmpty())
  }

  @Test
  fun createTasks_addsMultipleTasksAtomically() = runBlocking {
    val now = System.currentTimeMillis()
    val ids = store.createTasks(
      listOf(
        LocalTasksStore.NewTaskInput(
          title = "上午需求沟通",
          notes = "",
          deadlineMillis = now + 3_600_000L,
          priority = TaskPriority.HIGH,
          category = "沟通",
          remindMinutesBefore = 15L,
        ),
        LocalTasksStore.NewTaskInput(
          title = "下午代码提测",
          notes = "主分支合并",
          deadlineMillis = now + 7_200_000L,
          priority = TaskPriority.MEDIUM,
          category = "开发",
          remindMinutesBefore = 30L,
        ),
      )
    )
    assertEquals(2, ids.size)
    val all = store.getAllOnce()
    assertEquals(2, all.size)
    assertEquals("上午需求沟通", all[0].title)
    assertEquals("下午代码提测", all[1].title)
  }
}
