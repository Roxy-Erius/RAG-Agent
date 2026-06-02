package com.ragagent.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ragagent.databinding.ActivityProductDetailBinding
import com.ragagent.network.ApiService
import kotlinx.coroutines.launch

class ProductCardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PRODUCT_ID = "product_id"
    }

    private lateinit var binding: ActivityProductDetailBinding
    private val apiService = ApiService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val productId = intent.getStringExtra(EXTRA_PRODUCT_ID) ?: return finish()

        lifecycleScope.launch {
            val product = apiService.getProduct(productId)
            if (product != null) {
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
    }
}
