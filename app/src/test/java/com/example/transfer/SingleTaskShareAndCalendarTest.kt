package com.example.transfer

import android.content.Intent
import android.provider.CalendarContract
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.util.CalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SingleTaskShareAndCalendarTest {

  private fun sampleTask(): TaskItem = TaskItem(
    id = "task_share_1",
    title = "下午四点参加产品设计评审",
    notes = "确认折叠屏左滑删除交互",
    deadlineMillis = 1_800_000_000_000L,
    priority = TaskPriority.HIGH.wireValue,
    category = "会议",
    remindMinutesBefore = 15L,
  )

  @Test
  fun `single task share token encodes and decodes cleanly`() {
    val task = sampleTask()
    val token = SingleTaskShareCodec.encodeToken(task)
    assertTrue(token.startsWith(SingleTaskShareCodec.TOKEN_PREFIX))

    val decoded = SingleTaskShareCodec.decodeToken(token)
    assertTrue(decoded.isSuccess)
    val payload = decoded.getOrThrow()
    assertEquals(task.title, payload.title)
    assertEquals(task.notes, payload.notes)
    assertEquals(task.deadlineMillis, payload.deadlineMillis)
    assertEquals(task.priority, payload.priority)
    assertEquals(task.category, payload.category)
    assertEquals(task.remindMinutesBefore, payload.remindMinutesBefore)

    val record = payload.toNewLocalRecord()
    assertTrue(record.id.startsWith("local_"))
    assertEquals(task.title, record.title)
  }

  @Test
  fun `qr code bitmap generation and decoding round trip succeeds`() {
    val task = sampleTask()
    val token = SingleTaskShareCodec.encodeToken(task)

    val bitmap = QrCodeHelper.generateQrBitmap(token, sizePx = 420)
    assertNotNull(bitmap)

    val decodedRaw = QrCodeHelper.decodeQrFromBitmap(bitmap!!)
    assertEquals(token, decodedRaw)

    val payload = SingleTaskShareCodec.decodeToken(decodedRaw!!).getOrThrow()
    assertEquals(task.title, payload.title)
  }

  @Test
  fun `calendar helper builds valid system calendar insert intent`() {
    val task = sampleTask()
    val intent = CalendarHelper.buildInsertEventIntent(task)

    assertEquals(Intent.ACTION_INSERT, intent.action)
    assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
    assertEquals(task.title, intent.getStringExtra(CalendarContract.Events.TITLE))
    assertEquals(task.deadlineMillis, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0L))
    assertEquals(
      task.deadlineMillis + 30 * 60_000L,
      intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0L),
    )
    assertTrue(intent.getBooleanExtra(CalendarContract.Events.HAS_ALARM, false))
  }
}
