package com.ragagent.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ragagent.databinding.ActivityMainBinding
import com.ragagent.model.ChatMessage
import com.ragagent.model.Product
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
                    putExtra(ProductCardActivity.EXTRA_SESSION_ID, viewModel.sessionId)
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

        // 观察个性化推荐
        lifecycleScope.launch {
            viewModel.recommendations.collect { products ->
                if (products.isNotEmpty()) {
                    binding.tvRecommendTitle.visibility = View.VISIBLE
                    binding.recyclerRecommendations.visibility = View.VISIBLE
                    binding.recyclerRecommendations.adapter = ProductRecommendAdapter(products) { product ->
                        val intent = Intent(this@MainActivity, ProductCardActivity::class.java).apply {
                            putExtra(ProductCardActivity.EXTRA_PRODUCT_ID, product.productId)
                            putExtra(ProductCardActivity.EXTRA_SESSION_ID, viewModel.sessionId)
                        }
                        startActivity(intent)
                    }
                } else {
                    binding.tvRecommendTitle.visibility = View.GONE
                    binding.recyclerRecommendations.visibility = View.GONE
                }
            }
        }
        viewModel.loadRecommendations()

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

    /** 个性化推荐横向卡片适配器 */
    inner class ProductRecommendAdapter(
        private val products: List<Product>,
        private val onClick: (Product) -> Unit
    ) : RecyclerView.Adapter<ProductRecommendAdapter.ViewHolder>() {

        inner class ViewHolder(val card: CardView) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val card = CardView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    180.dpToPx(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 12.dpToPx() }
                radius = 14.dpToPx().toFloat()
                cardElevation = 3.dpToPx().toFloat()
                setCardBackgroundColor(Color.parseColor("#FFFFFF"))
                isClickable = true
                isFocusable = true
            }

            val content = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(10.dpToPx(), 10.dpToPx(), 10.dpToPx(), 10.dpToPx())
            }

            val image = ImageView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 120.dpToPx())
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#F5EFE5"))
                id = View.generateViewId()
            }
            content.addView(image)

            val title = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8.dpToPx() }
                textSize = 13f
                setTextColor(Color.parseColor("#5C4A3A"))
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                id = View.generateViewId()
            }
            content.addView(title)

            val price = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4.dpToPx() }
                textSize = 15f
                setTextColor(Color.parseColor("#C47A4A"))
                id = View.generateViewId()
            }
            content.addView(price)

            card.addView(content)
            return ViewHolder(card)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val product = products[position]
            val content = holder.card.getChildAt(0) as LinearLayout
            val title = content.findViewById<TextView>(content.getChildAt(1).id)
            val price = content.findViewById<TextView>(content.getChildAt(2).id)
            title.text = product.title
            price.text = "¥${product.basePrice.toInt()}"
            holder.card.setOnClickListener { onClick(product) }
        }

        override fun getItemCount(): Int = products.size

        private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    }
}
