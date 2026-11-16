package com.example.transfer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LocalTaskRecord
import com.example.data.LocalTasksStore
import com.example.model.TaskPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DevicePairingTransferTest {

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
  fun `lan pairing server transfers records on matching 6-digit code and rejects wrong code`() = runBlocking {
    val sampleRecords = listOf(
      LocalTaskRecord(
        id = "local_t1",
        title = "下午三点前提交季度汇报",
        notes = "附带折叠屏适配截图",
        deadlineMillis = 1_800_000_000_000L,
        priority = TaskPriority.HIGH.wireValue,
        category = "工作",
        remindMinutesBefore = 30L,
      ),
      LocalTaskRecord(
        id = "local_t2",
        title = "确认客服工单回复",
        deadlineMillis = 1_800_003_600_000L,
        priority = TaskPriority.MEDIUM.wireValue,
        category = "沟通",
      ),
    )

    val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var transferredCount = 0
    val server = DevicePairingTransfer.PairingServer(
      pairingCode = "492817",
      recordsProvider = { sampleRecords },
      onTransferredCount = { transferredCount = it },
    )

    try {
      val info = server.start(context = null, scope = serverScope)
      assertTrue(info.port > 0)

      val wrongAttempt = DevicePairingTransfer.fetchFromAddress("127.0.0.1:${info.port}", "000000")
      assertTrue(wrongAttempt.isFailure)

      val rightAttempt = DevicePairingTransfer.fetchFromAddress("127.0.0.1:${info.port}", "492817")
      assertTrue(rightAttempt.isSuccess)
      val bundle = rightAttempt.getOrThrow()
      assertEquals(2, bundle.records.size)
      assertEquals(2, transferredCount)

      val imported = store.importRecords(bundle.records, replaceExisting = false)
      assertEquals(2, imported)
      assertEquals(2, store.getAllOnce().size)

      // Importing identical records in merge mode deduplicates cleanly
      val duplicateImport = store.importRecords(bundle.records, replaceExisting = false)
      assertEquals(0, duplicateImport)
      assertEquals(2, store.getAllOnce().size)
    } finally {
      server.stop(context = null)
      serverScope.cancel()
    }
  }

  @Test
  fun `offline encrypted transfer package decrypts with exact code and rejects wrong code`() {
    val sampleRecords = listOf(
      LocalTaskRecord(
        id = "local_off_1",
        title = "核对新手机数据迁移",
        deadlineMillis = 1_800_010_000_000L,
        priority = TaskPriority.HIGH.wireValue,
        category = "设备",
      )
    )

    val token = DevicePairingTransfer.encodeOfflineEncryptedPackage(sampleRecords, "738291")
    assertTrue(token.startsWith("TD1:"))

    val wrongDecode = DevicePairingTransfer.decodeOfflineEncryptedPackage(token, "123456")
    assertTrue(wrongDecode.isFailure)

    val rightDecode = DevicePairingTransfer.decodeOfflineEncryptedPackage(token, "738291")
    assertTrue(rightDecode.isSuccess)
    val decodedRecords = rightDecode.getOrThrow().records
    assertEquals(1, decodedRecords.size)
    assertEquals("核对新手机数据迁移", decodedRecords.first().title)
  }

  @Test
  fun `importRecords replaceExisting overwrites existing tasks`() = runBlocking {
    store.createTask(
      title = "旧任务",
      notes = "",
      deadlineMillis = System.currentTimeMillis() + 3_600_000L,
      priority = TaskPriority.LOW,
      category = "工作",
      remindMinutesBefore = 15L,
    )
    assertEquals(1, store.getAllOnce().size)

    val incoming = listOf(
      LocalTaskRecord(
        id = "new_id_1",
        title = "迁移后的任务 A",
        deadlineMillis = System.currentTimeMillis() + 7_200_000L,
      ),
      LocalTaskRecord(
        id = "new_id_2",
        title = "迁移后的任务 B",
        deadlineMillis = System.currentTimeMillis() + 10_800_000L,
      ),
    )
    val imported = store.importRecords(incoming, replaceExisting = true)
    assertEquals(2, imported)
    val stored = store.getAllOnce()
    assertEquals(2, stored.size)
    assertTrue(stored.none { it.title == "旧任务" })
  }
}
