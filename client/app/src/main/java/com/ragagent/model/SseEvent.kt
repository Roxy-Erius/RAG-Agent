package com.ragagent.model

import com.google.gson.annotations.SerializedName

/**
 * SSE 事件类型，与后端 SSE 消息格式约定一致。
 */
sealed class SseEvent {
    data class Token(val content: String) : SseEvent()
    data class ProductRef(val productId: String) : SseEvent()
    data class AddToCart(val productId: String, val quantity: Int = 1, val mode: String = "add") : SseEvent()
    data class DeleteFromCart(val cartItemId: Long) : SseEvent()
    data object ClearCart : SseEvent()
    data class Done(val conversationId: String = "") : SseEvent()
    data class Error(val message: String) : SseEvent()
}

/**
 * Gson 反序列化的中间结构。
 */
data class SseEventDto(
    val type: String?,
    val content: String?,
    val productId: String?,
    val quantity: Int?,
    val cartItemId: Long?,
    val mode: String?,
    val conversationId: String?,
    val message: String?
) {
    fun toSseEvent(): SseEvent = when (type) {
        "token" -> SseEvent.Token(content ?: "")
        "product" -> SseEvent.ProductRef(productId ?: "")
        "add_to_cart" -> SseEvent.AddToCart(productId ?: "", quantity ?: 1, mode ?: "add")
        "delete_from_cart" -> SseEvent.DeleteFromCart(cartItemId ?: -1L)
        "clear_cart" -> SseEvent.ClearCart
        "done" -> SseEvent.Done(conversationId ?: "")
        "error" -> SseEvent.Error(message ?: "未知错误")
        else -> SseEvent.Error("未知消息类型: $type")
    }
}
