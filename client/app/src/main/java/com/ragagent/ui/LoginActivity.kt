package com.ragagent.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ragagent.auth.AuthManager
import com.ragagent.databinding.ActivityLoginBinding
import com.ragagent.network.ApiService
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val apiService = ApiService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            if (username.isEmpty() || password.isEmpty()) {
                binding.tvError.text = "请输入用户名和密码"
                binding.tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            binding.btnLogin.isEnabled = false
            lifecycleScope.launch {
                val resp = apiService.login(username, password)
                if (resp != null) {
                    AuthManager.saveToken(resp.token, resp.username)
                    val sessionId = intent.getStringExtra("sessionId") ?: "default"
                    apiService.linkSession(sessionId)
                    Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    binding.tvError.text = "用户名或密码错误"
                    binding.tvError.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = true
                }
            }
        }

        binding.tvGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
