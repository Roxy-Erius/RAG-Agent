package com.ragagent.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ragagent.auth.AuthManager
import com.ragagent.databinding.ActivityProductDetailBinding
import com.ragagent.model.Product
import com.ragagent.network.ApiService
import kotlinx.coroutines.launch
import java.util.UUID

class ProductCardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PRODUCT_ID = "product_id"
        const val EXTRA_SESSION_ID = "session_id"
    }

    private lateinit var binding: ActivityProductDetailBinding
    private val apiService = ApiService()
    private var currentProduct: Product? = null
    private val sessionId: String by lazy {
        intent.getStringExtra(EXTRA_SESSION_ID) ?: UUID.randomUUID().toString().take(8)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val productId = intent.getStringExtra(EXTRA_PRODUCT_ID) ?: return finish()

        lifecycleScope.launch {
            val product = apiService.getProduct(productId)
            if (product != null) {
                currentProduct = product
                binding.tvTitle.text = product.title
                binding.tvBrand.text = product.brand
                binding.tvPrice.text = "¥${product.basePrice}"
                binding.tvDescription.text = product.marketingDescription ?: "暂无描述"

                if (!product.imagePath.isNullOrBlank()) {
                    com.bumptech.glide.Glide.with(this@ProductCardActivity)
                        .load(product.imagePath)
                        .into(binding.ivProduct)
                }
            }
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnAddToCart.setOnClickListener {
            val product = currentProduct ?: return@setOnClickListener
            if (!AuthManager.isLoggedIn()) {
                showLoginDialog()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                apiService.addToCart(sessionId, product.productId)
                Toast.makeText(this@ProductCardActivity, "✅ 已加入购物车", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBuyNow.setOnClickListener {
            val product = currentProduct ?: return@setOnClickListener
            if (!AuthManager.isLoggedIn()) {
                showLoginDialog()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                apiService.addToCart(sessionId, product.productId)
                val intent = Intent(this@ProductCardActivity, CartActivity::class.java).apply {
                    putExtra("sessionId", sessionId)
                }
                startActivity(intent)
            }
        }
    }

    private fun showLoginDialog() {
        AlertDialog.Builder(this)
            .setTitle("请先登录")
            .setMessage("登录后即可使用购物车功能")
            .setPositiveButton("去登录") { _, _ ->
                startActivity(Intent(this, LoginActivity::class.java))
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
