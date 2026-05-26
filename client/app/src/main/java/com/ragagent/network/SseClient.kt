package com.ragagent.network

import com.ragagent.BuildConfig
import com.ragagent.model.SseEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * SSE 流式客户端 — 连接后端 /api/chat/stream 端点。
 */
class SseClient {

    private val baseUrl: String get() = BuildConfig.BASE_URL

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)  // SSE 长连接必须设长
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 发起 SSE 流式请求，返回 Flow<SseEvent>。
     */
    fun connect(message: String, sessionId: String): Flow<SseEvent> = callbackFlow {
        val url = "$baseUrl/api/chat/stream?message=${java.net.URLEncoder.encode(message, "UTF-8")}&sessionId=$sessionId"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val event = parseEvent(data)
                trySend(event)
                if (event is SseEvent.Done || event is SseEvent.Error) {
                    eventSource.cancel()
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                trySend(SseEvent.Error(t?.message ?: "连接失败"))
                close(t)
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val factory = EventSources.createFactory(client)
        val eventSource = factory.newEventSource(request, listener)

        awaitClose { eventSource.cancel() }
    }

    /**
     * 解析后端原始文本 SSE 事件。
     * 协议格式（见 API 文档 2.1 节）：
     *   每个 "data:" 行是一个原始文本 token
     *   "[DONE]"           → 对话结束
     *   "[PRODUCT:xxx]"    → 商品标记
     *   其他文本           → AI 回复内容
     */
    private fun parseEvent(data: String): SseEvent {
        return when {
            data == "[DONE]" -> SseEvent.Done
            data.matches(Regex("^\\[PRODUCT:\\w+\\]\$")) -> {
                val productId = data.removePrefix("[PRODUCT:").removeSuffix("]")
                SseEvent.ProductRef(productId)
            }
            else -> SseEvent.Token(data)
        }
    }
}
