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
                val events = parseEvents(data)
                events.forEach { event ->
                    trySend(event)
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
     * 解析后端原始文本 SSE 事件，支持 [PRODUCT:id] 标记嵌在文本中。
     * 协议格式（见 API 文档 2.1 节）：
     *   "data:[DONE]"       → 对话结束
     *   "data:[PRODUCT:xxx]"→ 商品标记（单行或内嵌）
     *   其他文本            → AI 回复内容
     */
    private val PRODUCT_TAG_REGEX = Regex("\\[PRODUCT:(\\w+)\\]")

    private fun parseEvents(data: String): List<SseEvent> {
        if (data == "[DONE]") return listOf(SseEvent.Done)

        val tags = PRODUCT_TAG_REGEX.findAll(data).toList()
        if (tags.isEmpty()) return listOf(SseEvent.Token(data))

        // 有内嵌商品标记 → 文本 + 商品引用分开推送
        val events = mutableListOf<SseEvent>()
        var lastEnd = 0
        for (match in tags) {
            val before = data.substring(lastEnd, match.range.first)
            if (before.isNotEmpty()) events.add(SseEvent.Token(before))
            events.add(SseEvent.ProductRef(match.groupValues[1]))
            lastEnd = match.range.last + 1
        }
        val after = data.substring(lastEnd)
        if (after.isNotEmpty()) events.add(SseEvent.Token(after))
        return events
    }
}
