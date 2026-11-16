package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.TaskPriority
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NaturalLanguageTaskParserTest {

  @Test
  fun parseDraftJson_handlesRawAndMarkdownWrappedJson() {
    val wrapped = """
      ```json
      {
        "title": "发送Q4财务汇报给张总",
        "notes": "附带预算对比表",
        "deadlineLocal": "2026-11-15 17:30",
        "priority": "high",
        "category": "沟通",
        "remindMinutesBefore": 30
      }
      ```
    """.trimIndent()

    val draft = NaturalLanguageTaskParser.parseDraftJson(
      rawJson = wrapped,
      fallbackSentence = "默认标题",
    )

    assertEquals("发送Q4财务汇报给张总", draft.title)
    assertEquals("附带预算对比表", draft.notes)
    assertEquals(TaskPriority.HIGH, draft.priority)
    assertEquals("沟通", draft.category)
    assertEquals(30L, draft.remindMinutesBefore)

    val cal = Calendar.getInstance().apply { timeInMillis = draft.deadlineMillis }
    assertEquals(2026, cal.get(Calendar.YEAR))
    assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH))
    assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals(17, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(30, cal.get(Calendar.MINUTE))
  }

  @Test
  fun parseSentence_requiresApiKeyForExternalProviders() = runBlocking {
    val parser = NaturalLanguageTaskParser()
    val deepSeekWithoutKey = LlmConfig(
      provider = LlmProvider.DEEPSEEK,
      apiKey = "",
      baseUrl = LlmProvider.DEEPSEEK.defaultBaseUrl,
      modelName = LlmProvider.DEEPSEEK.defaultModel,
    )
    val result = parser.parseSentence("下午五点开会", deepSeekWithoutKey)
    assertTrue(result.isFailure)
  }

  @Test
  fun preferencesStore_persistsLlmAndFoldableLayoutPreferences() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AppPreferencesStore(context)

    store.saveFoldablePreference(FoldableDisplayPreference.FULLSCREEN)
    store.saveLlmConfig(
      LlmConfig(
        provider = LlmProvider.QWEN,
        apiKey = "test-key-123",
        baseUrl = LlmProvider.QWEN.defaultBaseUrl,
        modelName = "qwen-max",
      )
    )

    val loadedConfig = store.getLlmConfigOnce()
    assertEquals(LlmProvider.QWEN, loadedConfig.provider)
    assertEquals("test-key-123", loadedConfig.apiKey)
    assertEquals("qwen-max", loadedConfig.modelName)
    assertTrue(loadedConfig.isReadyToUse)

    store.saveFoldablePreference(FoldableDisplayPreference.AUTO)
  }
}
