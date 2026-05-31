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
    }
}
