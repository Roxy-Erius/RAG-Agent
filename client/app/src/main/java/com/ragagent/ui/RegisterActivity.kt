package com.ragagent.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ragagent.databinding.ActivityRegisterBinding
import com.ragagent.network.ApiService
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private val apiService = ApiService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnRegister.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirm = binding.etPasswordConfirm.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                binding.tvError.text = "请填写完整信息"
                binding.tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (password.length < 6) {
                binding.tvError.text = "密码至少6位"
                binding.tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (password != confirm) {
                binding.tvError.text = "两次密码不一致"
                binding.tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            binding.btnRegister.isEnabled = false
            lifecycleScope.launch {
                val ok = apiService.register(username, password)
                if (ok) {
                    Toast.makeText(this@RegisterActivity, "注册成功，请登录", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    binding.tvError.text = "注册失败，用户名可能已存在"
                    binding.tvError.visibility = View.VISIBLE
                    binding.btnRegister.isEnabled = true
                }
            }
        }
    }
}
