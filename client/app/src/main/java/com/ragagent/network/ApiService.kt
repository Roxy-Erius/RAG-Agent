package com.ragagent.network

import com.google.gson.Gson
import com.ragagent.BuildConfig
import com.ragagent.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * REST API 调用 — 商品查询。
 */
class ApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val token = com.ragagent.auth.AuthManager.getToken()
            val request = if (token != null) {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else original
            chain.proceed(request)
        }
        .build()

    private val gson = Gson()
    private val baseUrl: String get() = BuildConfig.BASE_URL

    suspend fun getProduct(productId: String): Product? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/products/$productId")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { gson.fromJson(it, Product::class.java) }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getProductsBatch(ids: List<String>): List<Product> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/products/batch?ids=${ids.joinToString(",")}")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: "[]"
                val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, Product::class.java).type
                gson.fromJson<List<Product>>(json, type)
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 向量语义搜索。见 API 文档 1.3 节。
     */
    suspend fun searchProducts(
        query: String,
        topK: Int = 3,
        category: String? = null
    ): List<Product> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val urlBuilder = StringBuilder("$baseUrl/api/products/search?query=$encodedQuery&topK=$topK")
            if (!category.isNullOrBlank()) {
                urlBuilder.append("&category=${java.net.URLEncoder.encode(category, "UTF-8")}")
            }
            val request = Request.Builder().url(urlBuilder.toString()).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: "[]"
                val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, Product::class.java).type
                gson.fromJson<List<Product>>(json, type)
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 清除会话历史。见 API 文档 2.3 节。
     */
    suspend fun clearSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/chat/session/$sessionId")
                .delete()
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // ========== 购物车 API ==========

    data class CartItemDto(
        val id: Long,
        val sessionId: String,
        val productId: String,
        val quantity: Int,
        val productTitle: String?,
        val productBrand: String?,
        val productPrice: Double?,
        val productImagePath: String?
    )

    suspend fun addToCart(sessionId: String, productId: String, quantity: Int = 1): CartItemDto? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/cart/add?sessionId=$sessionId&productId=$productId&quantity=$quantity")
                    .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.string()?.let { gson.fromJson(it, CartItemDto::class.java) }
                } else null
            } catch (e: Exception) { null }
        }

    suspend fun removeFromCart(id: Long, sessionId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/cart/$id?sessionId=$sessionId")
                .delete()
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    suspend fun updateCartQuantity(id: Long, sessionId: String, quantity: Int): CartItemDto? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/cart/$id?sessionId=$sessionId&quantity=$quantity")
                    .put(okhttp3.RequestBody.create(null, ByteArray(0)))
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.string()?.let { gson.fromJson(it, CartItemDto::class.java) }
                } else null
            } catch (e: Exception) { null }
        }

    suspend fun getCart(sessionId: String): List<CartItemDto> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/cart?sessionId=$sessionId")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: "[]"
                val type = com.google.gson.reflect.TypeToken.getParameterized(
                    List::class.java, CartItemDto::class.java).type
                gson.fromJson(json, type)
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    // ========== 用户认证 ==========

    data class LoginResponse(val token: String, val username: String)

    suspend fun login(username: String, password: String): LoginResponse? =
        withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(mapOf("username" to username, "password" to password))
                val request = Request.Builder()
                    .url("$baseUrl/api/auth/login")
                    .post(okhttp3.RequestBody.create(
                        "application/json".toMediaType(), json))
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.string()?.let { gson.fromJson(it, LoginResponse::class.java) }
                } else null
            } catch (e: Exception) { null }
        }

    suspend fun register(username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(mapOf("username" to username, "password" to password))
                val request = Request.Builder()
                    .url("$baseUrl/api/auth/register")
                    .post(okhttp3.RequestBody.create(
                        "application/json".toMediaType(), json))
                    .build()
                client.newCall(request).execute().isSuccessful
            } catch (e: Exception) { false }
        }

    suspend fun linkSession(sessionId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/auth/link-session?sessionId=$sessionId")
                    .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                    .build()
                client.newCall(request).execute().isSuccessful
            } catch (e: Exception) { false }
        }

    // ========== 会话历史 API ==========

    data class ConversationDto(
        val id: Long,
        val userId: Long,
        val conversationId: String,
        val title: String?,
        val createdAt: String?,
        val updatedAt: String?
    )

    data class MessageDto(
        val id: Long,
        val conversationId: Long,
        val role: String,
        val content: String?,
        val productIds: String?,
        val createdAt: String?
    )

    suspend fun getConversations(): List<ConversationDto> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/conversations")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: "[]"
                val type = com.google.gson.reflect.TypeToken.getParameterized(
                    List::class.java, ConversationDto::class.java).type
                gson.fromJson(json, type)
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getConversationMessages(conversationId: String): List<MessageDto> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/conversations/$conversationId/messages")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = response.body?.string() ?: "[]"
                    val type = com.google.gson.reflect.TypeToken.getParameterized(
                        List::class.java, MessageDto::class.java).type
                    gson.fromJson(json, type)
                } else emptyList()
            } catch (e: Exception) { emptyList() }
        }

    suspend fun deleteConversation(conversationId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/conversations/$conversationId")
                    .delete()
                    .build()
                client.newCall(request).execute().isSuccessful
            } catch (e: Exception) { false }
        }
}
