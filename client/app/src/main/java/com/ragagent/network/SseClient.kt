package com.ragagent.network

import com.google.gson.Gson
import com.ragagent.BuildConfig
import com.ragagent.model.SseEvent
import com.ragagent.model.SseEventDto
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
 * 后端按技术路线 2.5 节发送结构化 JSON 事件。
 */
class SseClient {

    private val baseUrl: String get() = BuildConfig.BASE_URL
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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

    /** 解析后端结构化 JSON：{"type":"token","content":"..."} / {"type":"product","productId":"..."} / {"type":"done"} */
    private fun parseEvent(data: String): SseEvent {
        return try {
            val dto = gson.fromJson(data, SseEventDto::class.java)
            dto.toSseEvent()
        } catch (e: Exception) {
            SseEvent.Error("解析失败: ${e.message}")
        }
    }
}
