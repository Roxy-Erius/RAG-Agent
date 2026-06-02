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

    /** 当前 DB 会话 ID（登录用户），null 表示匿名或新会话 */
    private var conversationId: String? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _cartCount = MutableStateFlow(0)
    val cartCount: StateFlow<Int> = _cartCount.asStateFlow()

    private val _conversationTitle = MutableStateFlow<String?>(null)
    val conversationTitle: StateFlow<String?> = _conversationTitle.asStateFlow()

    private val _recommendations = MutableStateFlow<List<Product>>(emptyList())
    val recommendations: StateFlow<List<Product>> = _recommendations.asStateFlow()

    private var streamJob: Job? = null

    fun addToCart(productId: String, quantity: Int = 1) {
        viewModelScope.launch {
            val item = apiService.addToCart(sessionId, productId, quantity)
            if (item != null) {
                _cartCount.value = _cartCount.value + quantity
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isStreaming.value) return

        clearRecommendations()

        // 添加用户消息
        append(ChatMessage.User(text))
        append(ChatMessage.Loading)
        _isStreaming.value = true

        streamJob = viewModelScope.launch {
            var aiText = ""
            var loadingRemoved = false
            var aiMessageAppended = false
            val pendingProductIds = mutableListOf<String>()

            sseClient.connect(text, sessionId, conversationId).collect { event ->
                when (event) {
                    is SseEvent.Token -> {
                        if (!loadingRemoved) {
                            removeLoading()
                            loadingRemoved = true
                        }
                        if (!aiMessageAppended) {
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
                        // 暂存商品 ID，Done 时批量拉取，避免切后台丢卡片
                        pendingProductIds.add(event.productId)
                    }
                    is SseEvent.AddToCart -> {
                        if (!loadingRemoved) {
                            removeLoading()
                            loadingRemoved = true
                        }
                        if (event.mode == "set") {
                            viewModelScope.launch {
                                apiService.setCartQuantity(sessionId, event.productId, event.quantity)
                                refreshCartCount()
                            }
                        } else {
                            addToCart(event.productId, event.quantity)
                        }
                    }
                    is SseEvent.DeleteFromCart -> {
                        if (!loadingRemoved) {
                            removeLoading()
                            loadingRemoved = true
                        }
                        viewModelScope.launch {
                            apiService.removeFromCart(event.cartItemId, sessionId)
                            refreshCartCount()
                        }
                    }
                    is SseEvent.ClearCart -> {
                        if (!loadingRemoved) {
                            removeLoading()
                            loadingRemoved = true
                        }
                        viewModelScope.launch {
                            // 清空购物车：逐一删除所有项
                            val items = apiService.getCart(sessionId)
                            for (item in items) {
                                apiService.removeFromCart(item.id, sessionId)
                            }
                            refreshCartCount()
                        }
                    }
                    is SseEvent.Done -> {
                        if (!loadingRemoved) {
                            removeLoading()
                            loadingRemoved = true
                        }
                        // 流式结束后批量拉取商品卡片，避免切后台丢卡片
                        if (pendingProductIds.isNotEmpty()) {
                            fetchAndInsertProductCards(pendingProductIds)
                        }
                        if (event.conversationId.isNotBlank() && conversationId == null) {
                            conversationId = event.conversationId
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

    /** 开始新会话 */
    fun startNewChat() {
        streamJob?.cancel()
        _messages.value = emptyList()
        _isStreaming.value = false
        conversationId = null
        _conversationTitle.value = null
        loadRecommendations()
    }

    /** 加载历史会话消息，并还原商品卡片 */
    fun loadConversation(cid: String, title: String?) {
        streamJob?.cancel()
        clearRecommendations()
        _messages.value = emptyList()
        _isStreaming.value = false
        conversationId = cid
        _conversationTitle.value = title

        viewModelScope.launch {
            val msgs = apiService.getConversationMessages(cid)
            if (msgs.isNotEmpty()) {
                // 先构建基础消息列表
                val chatMsgs = mutableListOf<ChatMessage>()
                val allProductIds = mutableListOf<String>()

                for (msg in msgs) {
                    when (msg.role) {
                        "user" -> chatMsgs.add(ChatMessage.User(msg.content ?: ""))
                        "ai" -> {
                            // 去除所有标签，SSE 流中由后端剥离，历史消息需手动清除
                            val cleanContent = msg.content?.replace(
                                Regex("\\[(PRODUCT|ADD_TO_CART|DELETE_FROM_CART|CLEAR_CART)[^\\]]*\\]"), ""
                            )?.trim() ?: ""
                            chatMsgs.add(ChatMessage.Ai(cleanContent))
                            // 收集该 AI 消息中引用的商品 ID
                            msg.productIds?.let { idsJson ->
                                val ids = parseProductIds(idsJson)
                                allProductIds.addAll(ids)
                                // 为每个 ID 先占位 null，后续批量拉取后回填
                                ids.forEach { _ -> chatMsgs.add(ChatMessage.Ai("")) }
                            }
                        }
                    }
                }

                _messages.value = chatMsgs

                // 批量拉取商品，回填到占位位置
                if (allProductIds.isNotEmpty()) {
                    val products = apiService.getProductsBatch(allProductIds)
                    val productMap = products.associateBy { it.productId }
                    val finalList = _messages.value.toMutableList()
                    var offset = 0
                    for (msg in msgs) {
                        if (msg.role == "ai") {
                            offset++ // skip the Ai message itself
                            msg.productIds?.let { idsJson ->
                                val ids = parseProductIds(idsJson)
                                for (id in ids) {
                                    val product = productMap[id]
                                    if (product != null && offset < finalList.size) {
                                        finalList[offset] = ChatMessage.ProductCard(product)
                                    }
                                    offset++
                                }
                            }
                        } else {
                            offset++
                        }
                    }
                    _messages.value = finalList
                }
            }
        }
    }

    /** 解析 JSON 数组字符串如 ["p_digital_007","p_beauty_003"] */
    private fun parseProductIds(json: String): List<String> {
        return try {
            com.google.gson.Gson().fromJson(
                json,
                Array<String>::class.java
            ).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getConversationId(): String? = conversationId

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

    private fun fetchAndInsertProductCard(productId: String) {
        viewModelScope.launch {
            val product = apiService.getProduct(productId)
            if (product != null) {
                append(ChatMessage.ProductCard(product))
            }
        }
    }

    /** 批量拉取多个商品并插入卡片（一次 API 调用，避免逐个查询的延迟） */
    private fun fetchAndInsertProductCards(productIds: List<String>) {
        viewModelScope.launch {
            val products = apiService.getProductsBatch(productIds)
            products.forEach { append(ChatMessage.ProductCard(it)) }
        }
    }

    fun refreshCartCount() {
        viewModelScope.launch {
            val items = apiService.getCart(sessionId)
            _cartCount.value = items.size
        }
    }

    fun loadRecommendations() {
        if (!com.ragagent.auth.AuthManager.isLoggedIn()) return
        viewModelScope.launch {
            val products = apiService.getRecommendations()
            _recommendations.value = products
        }
    }

    fun clearRecommendations() {
        _recommendations.value = emptyList()
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
