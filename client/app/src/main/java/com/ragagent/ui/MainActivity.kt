package com.ragagent.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ragagent.databinding.ActivityMainBinding
import com.ragagent.model.ChatMessage
import com.ragagent.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatAdapter

    companion object {
        private const val REQUEST_HISTORY = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ChatViewModel()

        adapter = ChatAdapter(
            onProductClick = { product ->
                val intent = Intent(this, ProductCardActivity::class.java).apply {
                    putExtra(ProductCardActivity.EXTRA_PRODUCT_ID, product.productId)
                }
                startActivity(intent)
            },
            onAddToCart = { product ->
                viewModel.addToCart(product.productId)
                Toast.makeText(this, "✅ 已加入购物车", Toast.LENGTH_SHORT).show()
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // 观察消息列表变化
        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                adapter.submitList(messages) {
                    binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }

        // 观察流式状态
        lifecycleScope.launch {
            viewModel.isStreaming.collect { isStreaming ->
                binding.btnSend.isEnabled = !isStreaming
                binding.progressBar.visibility = if (isStreaming) View.VISIBLE else View.GONE
            }
        }

        // 发送按钮
        binding.btnSend.setOnClickListener {
            val text = binding.etInput.text.toString().trim()
            if (text.isNotBlank()) {
                binding.etInput.text?.clear()
                viewModel.sendMessage(text)
            }
        }

        // 购物车角标
        lifecycleScope.launch {
            viewModel.cartCount.collect { count ->
                binding.tvCartBadge.text = count.toString()
                binding.tvCartBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
            }
        }

        binding.btnCart.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java).apply {
                putExtra("sessionId", viewModel.sessionId)
            })
        }

        // 历史会话按钮
        binding.btnHistory.setOnClickListener {
            if (!com.ragagent.auth.AuthManager.isLoggedIn()) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("请先登录")
                    .setMessage("登录后可查看历史会话")
                    .setPositiveButton("去登录") { _, _ ->
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                startActivityForResult(
                    Intent(this, HistoryActivity::class.java), REQUEST_HISTORY)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_HISTORY && resultCode == RESULT_OK) {
            val conversationId = data?.getStringExtra("conversationId")
            val title = data?.getStringExtra("title")
            if (!conversationId.isNullOrBlank()) {
                viewModel.loadConversation(conversationId, title)
                Toast.makeText(this, "已加载: ${title ?: "历史会话"}", Toast.LENGTH_SHORT).show()
            } else {
                // 新建对话
                viewModel.startNewChat()
                Toast.makeText(this, "已创建新对话", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshCartCount()
    }
}
