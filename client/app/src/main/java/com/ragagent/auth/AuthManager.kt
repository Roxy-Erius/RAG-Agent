package com.ragagent.auth

import android.content.Context
import android.content.SharedPreferences

object AuthManager {
    private const val PREFS_NAME = "rag_auth"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USERNAME = "username"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(token: String, username: String) {
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_USERNAME, username).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun isLoggedIn(): Boolean = getToken() != null

    fun logout() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USERNAME).apply()
    }
}
