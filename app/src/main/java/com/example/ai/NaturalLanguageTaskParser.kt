package com.example.ai

import android.util.Log
import com.example.model.TaskPriority
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class ParsedTaskDraft(
  val title: String,
  val notes: String,
  val deadlineMillis: Long,
  val priority: TaskPriority,
  val category: String,
  val remindMinutesBefore: Long,
)

class NaturalLanguageTaskParser {

  private val httpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(45, TimeUnit.SECONDS)
      .writeTimeout(20, TimeUnit.SECONDS)
      .build()
  }

  private val geminiStructuredModel by lazy {
    val taskItemSchema = Schema.obj(
      properties = mapOf(
        "title" to Schema.string(description = "Concise Chinese task title, 2 to 60 characters"),
        "notes" to Schema.string(description = "Optional extra context, deliverables, or participants"),
        "deadlineLocal" to Schema.string(description = "Deadline in yyyy-MM-dd HH:mm local time"),
        "priority" to Schema.enumeration(
          values = listOf("high", "medium", "low"),
          description = "Urgency level: high, medium, or low",
        ),
        "category" to Schema.enumeration(
          values = listOf("工作", "会议", "沟通", "开发", "个人"),
          description = "Category in Chinese",
        ),
        "remindMinutesBefore" to Schema.integer(
          description = "Lead reminder minutes before deadline, one of 0, 5, 15, 30, 60",
        ),
      ),
      optionalProperties = listOf("notes"),
    )
    val rootSchema = Schema.obj(
      properties = mapOf(
        "tasks" to Schema.array(
          items = taskItemSchema,
          description = "List of 1 or more extracted tasks from the user input",
        ),
      ),
    )
    val config = generationConfig {
      responseMimeType = "application/json"
      responseSchema = rootSchema
      temperature = 0.2f
    }
    Firebase.ai.generativeModel(
      modelName = LlmProvider.GEMINI_BUILTIN.defaultModel,
      generationConfig = config,
    )
  }

  suspend fun parseSentence(
    sentence: String,
    config: LlmConfig,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<ParsedTaskDraft> {
    return parseSentenceMultiple(sentence, config, nowMillis).map { drafts ->
      drafts.first()
    }
  }

  suspend fun parseSentenceMultiple(
    sentence: String,
    config: LlmConfig,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<List<ParsedTaskDraft>> {
    val trimmed = sentence.trim()
    if (trimmed.isBlank()) {
      return Result.failure(IllegalArgumentException("请输入要记录的工作内容与时间"))
    }
    if (config.provider.requiresApiKey && config.apiKey.isBlank()) {
      return Result.failure(IllegalStateException("当前选择了 ${config.provider.displayName}，请先在设置中填写 API Key 或切换为内置 Gemini"))
    }

    val prompt = buildParsingPrompt(trimmed, nowMillis)

    return try {
      val rawJson = when (config.provider) {
        LlmProvider.GEMINI_BUILTIN -> callFirebaseGemini(prompt)
        LlmProvider.ANTHROPIC_CLAUDE -> callAnthropicClaude(prompt, config)
        else -> callOpenAiCompatible(prompt, config)
      }
      val drafts = parseDraftsJson(rawJson = rawJson, fallbackSentence = trimmed, nowMillis = nowMillis)
      if (drafts.isEmpty()) {
        Result.failure(IllegalStateException("未识别出有效的待办事项，请换个说法重试"))
      } else {
        Result.success(drafts)
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "LLM parsing timed out", e)
      Result.failure(IllegalStateException("大模型解析超时，请检查网络或重试"))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "LLM parsing failed for ${config.provider}", e)
      Result.failure(IllegalStateException(e.localizedMessage ?: "大模型解析失败，请检查模型配置与网络"))
    }
  }

  suspend fun testConnection(config: LlmConfig): Result<String> {
    if (config.provider.requiresApiKey && config.apiKey.isBlank()) {
      return Result.failure(IllegalArgumentException("请先填写 API Key"))
    }
    return try {
      val testPrompt = "Reply with strictly this JSON: {\"tasks\":[{\"title\":\"测试\",\"notes\":\"\",\"deadlineLocal\":\"2026-12-31 18:00\",\"priority\":\"medium\",\"category\":\"工作\",\"remindMinutesBefore\":15}]}"
      val raw = when (config.provider) {
        LlmProvider.GEMINI_BUILTIN -> callFirebaseGemini(testPrompt)
        LlmProvider.ANTHROPIC_CLAUDE -> callAnthropicClaude(testPrompt, config)
        else -> callOpenAiCompatible(testPrompt, config)
      }
      if (raw.isBlank()) {
        Result.failure(IllegalStateException("模型返回内容为空"))
      } else {
        Result.success("连接成功：${config.provider.shortLabel} (${config.modelName}) 响应正常")
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Connection test failed", e)
      Result.failure(IllegalStateException(e.localizedMessage ?: "连接测试失败"))
    }
  }

  private suspend fun callFirebaseGemini(prompt: String): String {
    val response = withTimeout(45_000L) {
      geminiStructuredModel.generateContent(prompt)
    }
    return response.text ?: throw IllegalStateException("Gemini 返回结果为空")
  }

  private suspend fun callOpenAiCompatible(prompt: String, config: LlmConfig): String =
    withContext(Dispatchers.IO) {
      withTimeout(45_000L) {
        val cleanBase = config.baseUrl.trim().trimEnd('/')
        val endpoint = if (cleanBase.endsWith("/chat/completions")) {
          cleanBase
        } else {
          "$cleanBase/chat/completions"
        }

        val firstPayload = JSONObject().apply {
          put("model", config.modelName.trim())
          put("temperature", 0.2)
          put(
            "messages",
            JSONArray().apply {
              put(
                JSONObject().apply {
                  put("role", "system")
                  put("content", SYSTEM_PROMPT)
                }
              )
              put(
                JSONObject().apply {
                  put("role", "user")
                  put("content", prompt)
                }
              )
            },
          )
          put("response_format", JSONObject().put("type", "json_object"))
        }

        val firstResult = executeJsonPost(
          url = endpoint,
          headers = mapOf("Authorization" to "Bearer ${config.apiKey.trim()}"),
          bodyJson = firstPayload.toString(),
        )

        val responseStr = if (firstResult.isFailure && firstResult.exceptionOrNull()?.message?.contains("400") == true) {
          firstPayload.remove("response_format")
          executeJsonPost(
            url = endpoint,
            headers = mapOf("Authorization" to "Bearer ${config.apiKey.trim()}"),
            bodyJson = firstPayload.toString(),
          ).getOrThrow()
        } else {
          firstResult.getOrThrow()
        }

        val root = JSONObject(responseStr)
        val choices = root.optJSONArray("choices")
          ?: throw IllegalStateException("接口未返回 choices 字段")
        if (choices.length() == 0) throw IllegalStateException("模型未返回候选结果")
        val content = choices.getJSONObject(0)
          .optJSONObject("message")
          ?.optString("content")
          .orEmpty()
        if (content.isBlank()) throw IllegalStateException("模型返回内容为空")
        content
      }
    }

  private suspend fun callAnthropicClaude(prompt: String, config: LlmConfig): String =
    withContext(Dispatchers.IO) {
      withTimeout(45_000L) {
        val cleanBase = config.baseUrl.trim().trimEnd('/')
        val endpoint = if (cleanBase.endsWith("/messages")) {
          cleanBase
        } else {
          "$cleanBase/messages"
        }

        val payload = JSONObject().apply {
          put("model", config.modelName.trim())
          put("max_tokens", 1024)
          put("temperature", 0.2)
          put("system", SYSTEM_PROMPT)
          put(
            "messages",
            JSONArray().apply {
              put(
                JSONObject().apply {
                  put("role", "user")
                  put("content", prompt)
                }
              )
            },
          )
        }

        val responseStr = executeJsonPost(
          url = endpoint,
          headers = mapOf(
            "x-api-key" to config.apiKey.trim(),
            "anthropic-version" to "2023-06-01",
          ),
          bodyJson = payload.toString(),
        ).getOrThrow()

        val root = JSONObject(responseStr)
        val contentArr = root.optJSONArray("content")
          ?: throw IllegalStateException("Claude 接口未返回 content 字段")
        val textBuilder = StringBuilder()
        for (i in 0 until contentArr.length()) {
          val item = contentArr.getJSONObject(i)
          if (item.optString("type") == "text") {
            textBuilder.append(item.optString("text"))
          }
        }
        val text = textBuilder.toString()
        if (text.isBlank()) throw IllegalStateException("Claude 返回内容为空")
        text
      }
    }

  private fun executeJsonPost(
    url: String,
    headers: Map<String, String>,
    bodyJson: String,
  ): Result<String> {
    val mediaType = "application/json; charset=utf-8".toMediaType()
    val requestBuilder = Request.Builder()
      .url(url)
      .post(bodyJson.toRequestBody(mediaType))
      .addHeader("Content-Type", "application/json")
    headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

    return try {
      httpClient.newCall(requestBuilder.build()).execute().use { response ->
        val bodyText = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          val apiMsg = runCatching {
            JSONObject(bodyText).optJSONObject("error")?.optString("message")
          }.getOrNull()?.takeIf { it.isNotBlank() } ?: bodyText.take(180)
          Result.failure(IllegalStateException("HTTP ${response.code}: $apiMsg"))
        } else {
          Result.success(bodyText)
        }
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  companion object {
    private const val TAG = "NLTaskParser"

    private const val SYSTEM_PROMPT =
      "You extract one OR multiple structured work tasks from a Chinese or English user sentence. " +
        "If the user mentions several independent actions or deadlines in one sentence, split them into separate task objects inside the 'tasks' array. " +
        "Return ONLY valid JSON of the form {\"tasks\": [{title, notes, deadlineLocal (yyyy-MM-dd HH:mm), priority (high|medium|low), category (工作|会议|沟通|开发|个人), remindMinutesBefore (0|5|15|30|60)}]}."

    fun buildParsingPrompt(sentence: String, nowMillis: Long): String {
      val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm EEEE", Locale.CHINA)
      val nowFormatted = fmt.format(Date(nowMillis))
      val tzId = TimeZone.getDefault().id
      return """
        当前本地时间：$nowFormatted (时区: $tzId)
        请分析下面这句用户输入，识别其中包含的 1 个或多个独立工作待办事项（如果一句话里有多个时间或多项不同工作，请拆分为多条），严格只返回 JSON 对象 `{"tasks": [...]}`（不要额外解释）：
        用户输入：「$sentence」

        每条待办对象字段要求：
        - title: 提炼后的清晰工作标题（简明扼要，不超过60字）
        - notes: 补充说明、交付物或关联人物；如果没有则为空字符串 ""
        - deadlineLocal: 格式必须为 "yyyy-MM-dd HH:mm"。若用户只说了"上午10点/下班前/今天下午/今晚/3点"，请按当前时间合理推算；若某项未单独提时间，可继承句中主时间或默认为当前时间 1 小时后
        - priority: 仅限 "high"、"medium"、"low"（含"急/马上/立刻/重要/必须"取 "high"）
        - category: 仅限 "工作"、"会议"、"沟通"、"开发"、"个人"
        - remindMinutesBefore: 仅限整数 0、5、15、30、60（默认 15）
      """.trimIndent()
    }

    fun parseDraftJson(
      rawJson: String,
      fallbackSentence: String,
      nowMillis: Long = System.currentTimeMillis(),
    ): ParsedTaskDraft {
      return parseDraftsJson(rawJson, fallbackSentence, nowMillis).first()
    }

    fun parseDraftsJson(
      rawJson: String,
      fallbackSentence: String,
      nowMillis: Long = System.currentTimeMillis(),
    ): List<ParsedTaskDraft> {
      val cleaned = extractJsonString(rawJson)
      if (cleaned.startsWith("[")) {
        val arr = JSONArray(cleaned)
        return parseJsonArray(arr, fallbackSentence, nowMillis)
      }
      val root = JSONObject(cleaned)
      val tasksArray = root.optJSONArray("tasks")
        ?: root.optJSONArray("items")
        ?: root.optJSONArray("todos")
      if (tasksArray != null) {
        val list = parseJsonArray(tasksArray, fallbackSentence, nowMillis)
        if (list.isNotEmpty()) return list
      }
      return listOf(parseSingleObject(root, fallbackSentence, nowMillis))
    }

    private fun parseJsonArray(
      arr: JSONArray,
      fallbackSentence: String,
      nowMillis: Long,
    ): List<ParsedTaskDraft> {
      val result = mutableListOf<ParsedTaskDraft>()
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        result.add(parseSingleObject(obj, fallbackSentence, nowMillis))
      }
      return result
    }

    private fun parseSingleObject(
      json: JSONObject,
      fallbackSentence: String,
      nowMillis: Long,
    ): ParsedTaskDraft {
      val title = json.optString("title")
        .trim()
        .ifBlank { fallbackSentence.trim().take(60) }
        .take(120)
      val notes = json.optString("notes").trim().take(500)
      val deadlineStr = json.optString("deadlineLocal").trim()
      val deadlineMillis = parseDeadlineLocalToMillis(deadlineStr, nowMillis)

      val priority = TaskPriority.fromWire(json.optString("priority").trim().lowercase(Locale.ROOT))

      val allowedCategories = setOf("工作", "会议", "沟通", "开发", "个人")
      val rawCat = json.optString("category").trim()
      val category = if (rawCat in allowedCategories) rawCat else "工作"

      val allowedRemind = listOf(0L, 5L, 15L, 30L, 60L)
      val rawRemind = json.optLong("remindMinutesBefore", 15L)
      val remindMinutes = allowedRemind.minByOrNull { kotlin.math.abs(it - rawRemind) } ?: 15L

      return ParsedTaskDraft(
        title = title,
        notes = notes,
        deadlineMillis = deadlineMillis,
        priority = priority,
        category = category,
        remindMinutesBefore = remindMinutes,
      )
    }

    fun extractJsonString(raw: String): String {
      val trimmed = raw.trim()
      val objStart = trimmed.indexOf('{')
      val arrStart = trimmed.indexOf('[')
      return if (arrStart in 0 until (if (objStart >= 0) objStart else Int.MAX_VALUE)) {
        val arrEnd = trimmed.lastIndexOf(']')
        if (arrEnd > arrStart) trimmed.substring(arrStart, arrEnd + 1) else trimmed
      } else if (objStart >= 0) {
        val objEnd = trimmed.lastIndexOf('}')
        if (objEnd > objStart) trimmed.substring(objStart, objEnd + 1) else trimmed
      } else {
        trimmed
      }
    }

    fun extractJsonObjectString(raw: String): String {
      val trimmed = raw.trim()
      val start = trimmed.indexOf('{')
      val end = trimmed.lastIndexOf('}')
      if (start >= 0 && end > start) {
        return trimmed.substring(start, end + 1)
      }
      return trimmed
    }

    fun parseDeadlineLocalToMillis(deadlineLocal: String, nowMillis: Long): Long {
      if (deadlineLocal.isNotBlank()) {
        val patterns = listOf("yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm", "yyyy-MM-dd'T'HH:mm")
        for (pattern in patterns) {
          val parsed = runCatching {
            val sdf = SimpleDateFormat(pattern, Locale.CHINA).apply { isLenient = false }
            sdf.parse(deadlineLocal.take(16))?.time
          }.getOrNull()
          if (parsed != null && parsed > 0L) {
            return parsed
          }
        }
      }
      val cal = Calendar.getInstance().apply {
        timeInMillis = nowMillis + 60 * 60_000L
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
      return cal.timeInMillis
    }
  }
}
