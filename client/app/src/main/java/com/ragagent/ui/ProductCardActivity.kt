package com.ragagent.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ragagent.R
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
    private var selectedSkuId: String? = null
    private var selectedSkuLabel: String? = null
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
            if (product == null) return@launch finish()
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
            apiService.recordBehavior(productId, "VIEW")

            // 并行加载规格/评价/FAQ
            setupSpecSummary(productId)
            loadReviews(productId)
            loadFaqs(productId)
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnAddToCart.setOnClickListener {
            val product = currentProduct ?: return@setOnClickListener
            if (!AuthManager.isLoggedIn()) {
                showLoginDialog()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                apiService.addToCart(sessionId, product.productId, selectedSkuId, selectedSkuLabel)
                Toast.makeText(this@ProductCardActivity, "✅ 已加入购物车", Toast.LENGTH_SHORT).show()
                apiService.recordBehavior(product.productId, "CART")
            }
        }

        binding.btnBuyNow.setOnClickListener {
            if (!AuthManager.isLoggedIn()) {
                showLoginDialog()
                return@setOnClickListener
            }
            startActivity(Intent(this, CartActivity::class.java).apply {
                putExtra("sessionId", sessionId)
            })
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

    // ── 规格摘要 + BottomSheet ──────────────────────────

    private fun setupSpecSummary(productId: String) {
        lifecycleScope.launch {
            val skus = apiService.getProductSkus(productId)
            val skuCount = skus.size

            if (skus.isEmpty()) {
                selectedSkuId = null
                selectedSkuLabel = "标准"
                binding.tvSpecSelected.text = "已选：标准"
                binding.tvSpecCount.text = ""
            } else {
                val first = skus.firstOrNull()
                val propsJson = first?.get("properties")?.toString() ?: "{}"
                selectedSkuLabel = formatSkuLabel(propsJson)
                val raw = first?.get("sku_id")
                selectedSkuId = (raw as? String)?.takeIf { it.isNotBlank() }
                    ?: raw?.toString()?.takeIf { it.isNotBlank() }

                binding.tvSpecSelected.text = "已选：$selectedSkuLabel"
                binding.tvSpecCount.text = "共 $skuCount 种规格可选"

                val skuPrice = (first?.get("price") as? Double)
                if (skuPrice != null) binding.tvPrice.text = "¥$skuPrice"
            }

            binding.specSummaryRow.setOnClickListener {
                val product = currentProduct ?: return@setOnClickListener
                val dialog = SpecSheetDialog.newInstance(
                    productId = productId,
                    imagePath = product.imagePath,
                    basePrice = product.basePrice ?: 0.0,
                    currentSkuId = selectedSkuId,
                    currentSkuLabel = selectedSkuLabel
                )
                dialog.setOnSpecConfirmedListener(object : SpecSheetDialog.OnSpecConfirmedListener {
                    override fun onSpecConfirmed(skuId: String?, skuLabel: String?, quantity: Int) {
                        selectedSkuId = skuId
                        selectedSkuLabel = skuLabel
                        binding.tvSpecSelected.text = "已选：${skuLabel ?: "标准"}"

                        lifecycleScope.launch {
                            val item = apiService.addToCart(
                                sessionId, product.productId, selectedSkuId, selectedSkuLabel, quantity
                            )
                            if (item != null) {
                                Toast.makeText(this@ProductCardActivity, "✅ 已加入购物车", Toast.LENGTH_SHORT).show()
                                apiService.recordBehavior(product.productId, "CART")
                            } else {
                                Toast.makeText(this@ProductCardActivity, "加入购物车失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                })
                dialog.show(supportFragmentManager, SpecSheetDialog.TAG)
            }
        }
    }

    private fun formatSkuLabel(propsJson: String): String {
        if (propsJson.isBlank() || propsJson == "{}") return "默认"
        return try {
            val map = com.google.gson.Gson().fromJson<Map<String, String>>(
                propsJson, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            )
            map.values.joinToString(" / ")
        } catch (e: Exception) {
            propsJson.replace("{", "").replace("}", "").replace("\"", "")
                .replace(",", " / ").replace(":", ": ")
        }
    }

    // ── 用户评价 ──────────────────────────────────────

    private fun loadReviews(productId: String) {
        lifecycleScope.launch {
            val reviews = apiService.getProductReviews(productId)
            if (reviews.isEmpty()) return@launch
            binding.reviewsContainer.removeAllViews()

            val showCount = minOf(3, reviews.size)
            for (i in 0 until showCount) {
                binding.reviewsContainer.addView(buildReviewCard(reviews[i]))
                if (i < showCount - 1) binding.reviewsContainer.addView(createSpacer(10.dpToPx()))
            }

            if (reviews.size > 3) {
                binding.tvViewMoreReviews.visibility = View.VISIBLE
                binding.tvViewMoreReviews.setOnClickListener {
                    val expanded = binding.tvViewMoreReviews.tag as? Boolean ?: false
                    binding.reviewsContainer.removeAllViews()
                    val count = if (expanded) minOf(3, reviews.size) else reviews.size
                    for (i in 0 until count) {
                        binding.reviewsContainer.addView(buildReviewCard(reviews[i]))
                        if (i < count - 1) binding.reviewsContainer.addView(createSpacer(10.dpToPx()))
                    }
                    binding.tvViewMoreReviews.text = if (expanded) "查看全部评价 →" else "收起评价 ↑"
                    binding.tvViewMoreReviews.tag = !expanded
                }
            }
        }
    }

    private fun buildReviewCard(r: Map<String, Any>): android.widget.FrameLayout {
        val nickname = r["nickname"]?.toString() ?: "匿名"
        val rating = (r["rating"] as? Double)?.toInt() ?: 0
        val content = r["content"]?.toString() ?: ""
        val stars = "★".repeat(rating) + "☆".repeat(5 - rating)

        val card = android.widget.FrameLayout(this).apply {
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColors(intArrayOf(0xFFFDF8F2.toInt(), 0xFFFEFCF9.toInt()))
                orientation = android.graphics.drawable.GradientDrawable.Orientation.TL_BR
                cornerRadius = 14.dpToPx().toFloat()
                setStroke(1.dpToPx(), 0xFFF0E7DD.toInt())
            }
            background = bg
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 14.dpToPx())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val textBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        textBlock.addView(TextView(this).apply {
            text = "$nickname  $stars"
            setTextColor(0xFF5C4A3A.toInt()); textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        val contentTv = TextView(this).apply {
            text = content; setTextColor(0xFF6B5A4A.toInt()); textSize = 13f
            setPadding(0, 6.dpToPx(), 0, 0); setLineSpacing(4.dpToPx().toFloat(), 1f)
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textBlock.addView(contentTv)
        card.addView(textBlock)

        // 点击卡片展开/收起
        var expanded = false
        card.setOnClickListener {
            expanded = !expanded
            contentTv.maxLines = if (expanded) Integer.MAX_VALUE else 2
        }
        return card
    }

    // ── FAQ ──────────────────────────────────────────

    private fun loadFaqs(productId: String) {
        lifecycleScope.launch {
            val faqs = apiService.getProductFaqs(productId)
            if (faqs.isEmpty()) return@launch
            binding.faqContainer.removeAllViews()

            val showCount = minOf(3, faqs.size)
            for (i in 0 until showCount) {
                binding.faqContainer.addView(buildFaqCard(faqs[i]))
                if (i < showCount - 1) binding.faqContainer.addView(createSpacer(8.dpToPx()))
            }

            if (faqs.size > 3) {
                binding.tvViewMoreFaqs.visibility = View.VISIBLE
                binding.tvViewMoreFaqs.setOnClickListener {
                    val expanded = binding.tvViewMoreFaqs.tag as? Boolean ?: false
                    binding.faqContainer.removeAllViews()
                    val count = if (expanded) minOf(3, faqs.size) else faqs.size
                    for (i in 0 until count) {
                        binding.faqContainer.addView(buildFaqCard(faqs[i]))
                        if (i < count - 1) binding.faqContainer.addView(createSpacer(8.dpToPx()))
                    }
                    binding.tvViewMoreFaqs.text = if (expanded) "查看全部问题 →" else "收起问题 ↑"
                    binding.tvViewMoreFaqs.tag = !expanded
                }
            }
        }
    }

    private fun buildFaqCard(faq: Map<String, Any>): LinearLayout {
        val q = faq["question"]?.toString() ?: ""
        val a = faq["answer"]?.toString() ?: ""

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 10.dpToPx().toFloat()
                setColor(0xFFFBF8F4.toInt())
            }
            background = bg
            setPadding(16.dpToPx(), 13.dpToPx(), 16.dpToPx(), 13.dpToPx())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        card.addView(TextView(this).apply {
            text = "Q: $q"; setTextColor(0xFF8B7355.toInt()); textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        val answerTv = TextView(this).apply {
            text = "A: $a"; setTextColor(0xFF5C4A3A.toInt()); textSize = 13f
            setPadding(0, 6.dpToPx(), 0, 0); setLineSpacing(3.dpToPx().toFloat(), 1f)
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        card.addView(answerTv)

        // 点击卡片展开/收起答案
        var expanded = false
        card.setOnClickListener {
            expanded = !expanded
            answerTv.maxLines = if (expanded) Integer.MAX_VALUE else 2
        }
        return card
    }

    private fun createSpacer(heightPx: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, heightPx)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
