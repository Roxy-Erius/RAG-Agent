package com.ragagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragagent.model.ChatMessage
import com.ragagent.model.Product
import com.ragagent.model.SseEvent
import com.ragagent.network.ApiService
import com.ragagent.network.SseClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel : ViewModel() {

    private val sseClient = SseClient()
    private val apiService = ApiService()

    /** 当前会话 ID，同一会话内保持上下文 */
    val sessionId: String = UUID.randomUUID().toString().take(8)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    /** 从完整回复文本中提取 [PRODUCT:xxx] 标记 */
    private val PRODUCT_TAG_REGEX = Regex("\\[PRODUCT:(\\w+)\\]")

    private var streamJob: Job? = null

    fun sendMessage(text: String) {
        if (text.isBlank() || _isStreaming.value) return

        // 添加用户消息
        append(ChatMessage.User(text))
        append(ChatMessage.Loading)
        _isStreaming.value = true

        streamJob = viewModelScope.launch {
            var aiText = ""
            var loadingRemoved = false
            var aiMessageAppended = false

            sseClient.connect(text, sessionId).collect { event ->
                when (event) {
                    is SseEvent.Token -> {
                        if (!loadingRemoved) {
                            removeLoading()
                            loadingRemoved = true
                        }
                        if (!aiMessageAppended) {
                            // 新消息首 token → 追加新 Ai 条目，而非覆盖上一条
                            append(ChatMessage.Ai(""))
                            aiMessageAppended = true
                        }
                        aiText += event.content
                        updateLastAiMessage(aiText)
                    }
                    is SseEvent.ProductRef -> {
                        if (!loadingRemoved) {
                            removeLoading()
                            loadingRemoved = true
                        }
                        // 确保当前 AI 文本已加入列表
                        if (aiText.isNotBlank()) {
                            finalizeLastAiMessage()
                            aiText = ""
                        }
                        // 获取商品信息并插入卡片
                        fetchAndInsertProductCard(event.productId)
                    }
                    is SseEvent.Done -> {
                        if (!loadingRemoved) {
                            removeLoading()
                            loadingRemoved = true
                        }
                        // 从完整回复中提取 [PRODUCT:id] 标记（后端 token 化拆散了标记）
                        val productIds = PRODUCT_TAG_REGEX.findAll(aiText)
                            .map { it.groupValues[1] }
                            .toList()
                        if (productIds.isNotEmpty()) {
                            // 去掉文字中的 [PRODUCT:xxx] 标记
                            updateLastAiMessage(aiText.replace(PRODUCT_TAG_REGEX, ""))
                            // 拉取商品并插入卡片
                            productIds.forEach { fetchAndInsertProductCard(it) }
                        }
                        _isStreaming.value = false
                    }
                    is SseEvent.Error -> {
                        if (!loadingRemoved) {
                            removeLoading()
                            loadingRemoved = true
                        }
                        append(ChatMessage.Ai("[错误] ${event.message}"))
                        _isStreaming.value = false
                    }
                }
            }
        }
    }

    private fun append(msg: ChatMessage) {
        _messages.value = _messages.value + msg
    }

    private fun removeLoading() {
        _messages.value = _messages.value.filter { it !is ChatMessage.Loading }
    }

    private fun updateLastAiMessage(text: String) {
        val list = _messages.value.toMutableList()
        val lastIndex = list.indexOfLast { it is ChatMessage.Ai }
        if (lastIndex >= 0) {
            list[lastIndex] = ChatMessage.Ai(text)
        } else {
            list.add(ChatMessage.Ai(text))
        }
        _messages.value = list
    }

    private fun finalizeLastAiMessage() {
        // 标记最后一个 AI 消息为完成状态（当前实现中已是不可变的）
    }

    private fun fetchAndInsertProductCard(productId: String) {
        viewModelScope.launch {
            val product = apiService.getProduct(productId)
            if (product != null) {
                append(ChatMessage.ProductCard(product))
            } else {
                append(ChatMessage.Ai("[DEBUG] 商品 $productId 未查到"))
            }
        }
    }

    fun clearSession() {
        streamJob?.cancel()
        _messages.value = emptyList()
        _isStreaming.value = false
        viewModelScope.launch {
            apiService.clearSession(sessionId)
        }
    }
}
