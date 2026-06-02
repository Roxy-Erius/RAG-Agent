package com.ragagent.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ragagent.auth.AuthManager
import com.ragagent.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    companion object {
        private const val REQUEST_LOGIN = 2001
        private const val REQUEST_REGISTER = 2002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 已登录 → 直接进主页
        if (AuthManager.isLoggedIn()) {
            goToMain()
            return
        }

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            startActivityForResult(Intent(this, LoginActivity::class.java), REQUEST_LOGIN)
        }

        binding.btnRegister.setOnClickListener {
            startActivityForResult(Intent(this, RegisterActivity::class.java), REQUEST_REGISTER)
        }

        binding.btnSkip.setOnClickListener {
            goToMain()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode == REQUEST_LOGIN || requestCode == REQUEST_REGISTER)
            && resultCode == RESULT_OK) {
            goToMain()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
