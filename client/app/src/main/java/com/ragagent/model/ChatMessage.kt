package com.ragagent.model

/**
 * RecyclerView 中的消息条目。
 */
sealed class ChatMessage {
    /** 用户发送的文本消息 */
    data class User(val text: String) : ChatMessage()

    /** AI 返回的文本消息（流式追加） */
    data class Ai(val text: String) : ChatMessage()

    /** AI 消息中嵌入的商品卡片 */
    data class ProductCard(val product: Product) : ChatMessage()

    /** 加载指示器 */
    data object Loading : ChatMessage()
}
