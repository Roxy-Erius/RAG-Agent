package com.ragagent.network

import com.google.gson.Gson
import com.ragagent.BuildConfig
import com.ragagent.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
}
