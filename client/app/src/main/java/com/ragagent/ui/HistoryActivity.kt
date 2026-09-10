package com.ragagent.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ragagent.auth.AuthManager
import com.ragagent.databinding.ActivityHistoryBinding
import com.ragagent.network.ApiService
import com.ragagent.network.ApiService.ConversationDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val apiService = ApiService()
    private val conversations = mutableListOf<ConversationDto>()
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnNewChat.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }

        // 登录/登出卡片
        binding.cardNotLoggedIn.setOnClickListener {
            startActivityForResult(Intent(this, LoginActivity::class.java), 3001)
        }
        binding.cardLoggedIn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？")
                .setPositiveButton("退出") { _, _ ->
                    AuthManager.logout()
                    updateAuthUI()
                    Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = HistoryAdapter(
            conversations,
            onClick = { conversation ->
                val intent = Intent()
                intent.putExtra("conversationId", conversation.conversationId)
                intent.putExtra("title", conversation.title)
                setResult(RESULT_OK, intent)
                finish()
            },
            onDelete = { conversation ->
                lifecycleScope.launch {
                    val ok = apiService.deleteConversation(conversation.conversationId)
                    if (ok) {
                        conversations.remove(conversation)
                        binding.recyclerHistory.adapter?.notifyDataSetChanged()
                        updateEmptyState()
                        Toast.makeText(this@HistoryActivity, "已删除", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@HistoryActivity, "删除失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        // 搜索框：300ms 防抖后重新加载
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300)
                    loadConversations()
                }
            }
        })

        updateAuthUI()
        loadConversations()
    }

    override fun onResume() {
        super.onResume()
        updateAuthUI()
        loadConversations()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 3001 && resultCode == RESULT_OK) {
            updateAuthUI()
            loadConversations()
        }
    }

    private fun updateAuthUI() {
        val loggedIn = AuthManager.isLoggedIn()
        if (loggedIn) {
            binding.cardNotLoggedIn.visibility = View.GONE
            binding.cardLoggedIn.visibility = View.VISIBLE
            binding.tvUsername.text = AuthManager.getUsername() ?: "用户"
            binding.divider.visibility = View.VISIBLE
            binding.etSearch.visibility = View.VISIBLE
        } else {
            binding.cardNotLoggedIn.visibility = View.VISIBLE
            binding.cardLoggedIn.visibility = View.GONE
            binding.divider.visibility = View.GONE
            binding.etSearch.visibility = View.GONE
            binding.etSearch.setText("")
            conversations.clear()
            binding.recyclerHistory.adapter?.notifyDataSetChanged()
            updateEmptyState()
        }
    }

    private fun loadConversations() {
        if (!AuthManager.isLoggedIn()) return
        lifecycleScope.launch {
            val query = binding.etSearch.text?.toString()
            val list = apiService.getConversations(query)
            conversations.clear()
            conversations.addAll(list)
            binding.recyclerHistory.adapter?.notifyDataSetChanged()
            updateEmptyState()
        }
    }

    private fun updateEmptyState() {
        val show = conversations.isEmpty()
        binding.tvEmpty.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.tvEmpty.text = if (!binding.etSearch.text.isNullOrBlank())
                "未找到匹配的历史会话" else "暂无历史会话\n登录后开始对话即可自动保存"
        }
    }

    companion object {
        const val RESULT_OK = android.app.Activity.RESULT_OK
    }
}
