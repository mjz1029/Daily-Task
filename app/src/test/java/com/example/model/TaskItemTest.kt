package com.example.model

import com.example.util.DeadlineFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskItemTest {

  @Test
  fun overdueAndTodayClassification_evaluatesAccurately() {
    val anchorNoon = DeadlineFormatter.todayAt(12, 0)
    val overdueTask = TaskItem(
      id = "t1",
      title = "早会纪要",
      deadlineMillis = anchorNoon - 30 * 60_000L,
      isCompleted = false,
    )
    val todayLaterTask = TaskItem(
      id = "t2",
      title = "提交日报",
      deadlineMillis = anchorNoon + 6 * 3600_000L,
      isCompleted = false,
    )
    val tomorrowTask = TaskItem(
      id = "t3",
      title = "明日评审",
      deadlineMillis = DeadlineFormatter.tomorrowAt(10, 0, anchorNoon),
      isCompleted = false,
    )

    assertTrue(overdueTask.isOverdue(anchorNoon))
    assertTrue(overdueTask.belongsToTodayActionList(anchorNoon))

    assertFalse(todayLaterTask.isOverdue(anchorNoon))
    assertTrue(todayLaterTask.isDueToday(anchorNoon))
    assertTrue(todayLaterTask.belongsToTodayActionList(anchorNoon))

    assertFalse(tomorrowTask.isDueToday(anchorNoon))
    assertFalse(tomorrowTask.belongsToTodayActionList(anchorNoon))
  }

  @Test
  fun deadlineFormatter_formatsRemainingAndOverdueLabels() {
    val now = DeadlineFormatter.todayAt(14, 0)
    val in45m = now + 45 * 60_000L
    val overdue15m = now - 15 * 60_000L

    assertEquals("剩余 45 分钟", DeadlineFormatter.formatRemainingLabel(in45m, false, now))
    assertEquals("已逾期 15 分钟", DeadlineFormatter.formatRemainingLabel(overdue15m, false, now))
    assertEquals("已完成", DeadlineFormatter.formatRemainingLabel(in45m, true, now))
  }
}
