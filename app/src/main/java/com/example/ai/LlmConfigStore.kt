package com.example.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_ai_and_layout_prefs")

enum class LlmProvider(
  val id: String,
  val displayName: String,
  val shortLabel: String,
  val defaultBaseUrl: String,
  val defaultModel: String,
  val requiresApiKey: Boolean,
  val description: String,
) {
  GEMINI_BUILTIN(
    id = "gemini_builtin",
    displayName = "Gemini（内置免配置）",
    shortLabel = "内置 Gemini",
    defaultBaseUrl = "",
    defaultModel = "gemini-3.8-flash",
    requiresApiKey = false,
    description = "通过 Firebase AI Logic 直接调用 Gemini 3.8 Flash，无需手动填写 API Key",
  ),
  DEEPSEEK(
    id = "deepseek",
    displayName = "DeepSeek",
    shortLabel = "DeepSeek",
    defaultBaseUrl = "https://api.deepseek.com/v1",
    defaultModel = "deepseek-chat",
    requiresApiKey = true,
    description = "高性价比中文语义理解，支持 DeepSeek-V3 / deepseek-chat",
  ),
  OPENAI(
    id = "openai",
    displayName = "OpenAI (GPT)",
    shortLabel = "OpenAI",
    defaultBaseUrl = "https://api.openai.com/v1",
    defaultModel = "gpt-4o-mini",
    requiresApiKey = true,
    description = "OpenAI 官方接口（gpt-4o-mini / gpt-4o / gpt-4.1-mini）",
  ),
  QWEN(
    id = "qwen",
    displayName = "通义千问 (Qwen)",
    shortLabel = "通义千问",
    defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    defaultModel = "qwen-plus",
    requiresApiKey = true,
    description = "阿里云百炼 OpenAI 兼容接口（qwen-plus / qwen-turbo）",
  ),
  KIMI(
    id = "kimi",
    displayName = "Kimi (Moonshot)",
    shortLabel = "Kimi",
    defaultBaseUrl = "https://api.moonshot.cn/v1",
    defaultModel = "moonshot-v1-8k",
    requiresApiKey = true,
    description = "月之暗面开放平台接口",
  ),
  GLM(
    id = "glm",
    displayName = "智谱 GLM",
    shortLabel = "智谱 GLM",
    defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
    defaultModel = "glm-4-flash",
    requiresApiKey = true,
    description = "智谱 AI 开放平台接口（glm-4-flash / glm-4-plus）",
  ),
  ANTHROPIC_CLAUDE(
    id = "claude",
    displayName = "Anthropic Claude",
    shortLabel = "Claude",
    defaultBaseUrl = "https://api.anthropic.com/v1",
    defaultModel = "claude-3-5-haiku-latest",
    requiresApiKey = true,
    description = "Anthropic Messages API",
  ),
  CUSTOM_OPENAI(
    id = "custom_openai",
    displayName = "自定义 OpenAI 兼容接口",
    shortLabel = "自定义模型",
    defaultBaseUrl = "https://api.openai.com/v1",
    defaultModel = "gpt-4o-mini",
    requiresApiKey = true,
    description = "兼容任意 OpenRouter / SiliconFlow / OneAPI / 本地 Ollama 网关",
  );

  companion object {
    fun fromId(id: String?): LlmProvider =
      entries.firstOrNull { it.id == id } ?: GEMINI_BUILTIN
  }
}

data class LlmConfig(
  val provider: LlmProvider = LlmProvider.GEMINI_BUILTIN,
  val apiKey: String = "",
  val baseUrl: String = LlmProvider.GEMINI_BUILTIN.defaultBaseUrl,
  val modelName: String = LlmProvider.GEMINI_BUILTIN.defaultModel,
) {
  val isReadyToUse: Boolean
    get() = !provider.requiresApiKey || (apiKey.isNotBlank() && baseUrl.isNotBlank() && modelName.isNotBlank())

  val badgeLabel: String
    get() = if (provider.requiresApiKey && apiKey.isBlank()) {
      "${provider.shortLabel}(未填Key)"
    } else {
      provider.shortLabel
    }
}

enum class FoldableDisplayPreference(
  val id: String,
  val labelZh: String,
  val subtitleZh: String,
) {
  AUTO(
    id = "auto",
    labelZh = "智能推荐",
    subtitleZh = "展开竖屏自动上下布局，横屏自动左右双栏",
  ),
  TOP_BOTTOM_SPLIT(
    id = "top_bottom",
    labelZh = "上下布局",
    subtitleZh = "上区看今日待办列表，下区常驻编辑与智能速记",
  ),
  FULLSCREEN(
    id = "fullscreen",
    labelZh = "全屏展示",
    subtitleZh = "整屏宽裕显示今日工作，点击卡片再弹窗编辑",
  ),
  LEFT_RIGHT_TWO_PANE(
    id = "left_right",
    labelZh = "左右双栏",
    subtitleZh = "左侧待办列表与右侧详情并排同屏处理",
  );

  companion object {
    fun fromId(id: String?): FoldableDisplayPreference =
      entries.firstOrNull { it.id == id } ?: AUTO
  }
}

class AppPreferencesStore(context: Context) {
  private val appContext = context.applicationContext

  fun observeLlmConfig(): Flow<LlmConfig> =
    appContext.appSettingsDataStore.data.map { prefs ->
      val provider = LlmProvider.fromId(prefs[KEY_PROVIDER])
      LlmConfig(
        provider = provider,
        apiKey = prefs[KEY_API_KEY].orEmpty(),
        baseUrl = prefs[KEY_BASE_URL]?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl,
        modelName = prefs[KEY_MODEL_NAME]?.takeIf { it.isNotBlank() } ?: provider.defaultModel,
      )
    }

  suspend fun getLlmConfigOnce(): LlmConfig = observeLlmConfig().first()

  suspend fun saveLlmConfig(config: LlmConfig) {
    appContext.appSettingsDataStore.edit { prefs ->
      prefs[KEY_PROVIDER] = config.provider.id
      prefs[KEY_API_KEY] = config.apiKey.trim()
      prefs[KEY_BASE_URL] = config.baseUrl.trim().ifBlank { config.provider.defaultBaseUrl }
      prefs[KEY_MODEL_NAME] = config.modelName.trim().ifBlank { config.provider.defaultModel }
    }
  }

  fun observeFoldablePreference(): Flow<FoldableDisplayPreference> =
    appContext.appSettingsDataStore.data.map { prefs ->
      FoldableDisplayPreference.fromId(prefs[KEY_FOLDABLE_PREF])
    }

  suspend fun saveFoldablePreference(preference: FoldableDisplayPreference) {
    appContext.appSettingsDataStore.edit { prefs ->
      prefs[KEY_FOLDABLE_PREF] = preference.id
    }
  }

  private companion object {
    val KEY_PROVIDER = stringPreferencesKey("llm_provider_id")
    val KEY_API_KEY = stringPreferencesKey("llm_api_key")
    val KEY_BASE_URL = stringPreferencesKey("llm_base_url")
    val KEY_MODEL_NAME = stringPreferencesKey("llm_model_name")
    val KEY_FOLDABLE_PREF = stringPreferencesKey("foldable_display_preference")
  }
}
