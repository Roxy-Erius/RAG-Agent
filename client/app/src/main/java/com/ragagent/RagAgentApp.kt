package com.ragagent

import android.app.Application

class RagAgentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.ragagent.auth.AuthManager.init(this)
    }
}


//不要小米的可以吗