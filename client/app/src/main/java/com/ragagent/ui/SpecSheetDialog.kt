package com.ragagent.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ragagent.R
import com.ragagent.auth.AuthManager
import com.ragagent.network.ApiService
import kotlinx.coroutines.launch

class SpecSheetDialog : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "SpecSheetDialog"
        private const val ARG_PRODUCT_ID = "product_id"
        private const val ARG_IMAGE_BASE64 = "image_base64"
        private const val ARG_BASE_PRICE = "base_price"
        private const val ARG_CURRENT_SKU_ID = "current_sku_id"
        private const val ARG_CURRENT_SKU_LABEL = "current_sku_label"

        fun newInstance(
            productId: String,
            imageBase64: String?,
            basePrice: Double,
            currentSkuId: String?,
            currentSkuLabel: String?
        ): SpecSheetDialog = SpecSheetDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_PRODUCT_ID, productId)
                putString(ARG_IMAGE_BASE64, imageBase64)
                putDouble(ARG_BASE_PRICE, basePrice)
                putString(ARG_CURRENT_SKU_ID, currentSkuId)
                putString(ARG_CURRENT_SKU_LABEL, currentSkuLabel)
            }
        }
    }

    interface OnSpecConfirmedListener {
        fun onSpecConfirmed(skuId: String?, skuLabel: String?, quantity: Int)
    }

    private data class SkuEntry(
        val skuId: String?,
        val properties: Map<String, String>,
        val price: Double
    )

    private val apiService = ApiService()
    private val gson = Gson()
    private var listener: OnSpecConfirmedListener? = null

    private val allSkus = mutableListOf<SkuEntry>()
    private val specDimensions = linkedMapOf<String, MutableList<String>>()
    private val selectedValues = mutableMapOf<String, String>()
    private var currentSkuId: String? = null
    private var currentSkuLabel: String? = null
    private var currentPrice: Double = 0.0
    private var quantity = 1

    private lateinit var ivProduct: ImageView
    private lateinit var tvPrice: TextView
    private lateinit var tvSelected: TextView
    private lateinit var tvStock: TextView
    private lateinit var specGroupsContainer: LinearLayout
    private lateinit var tvQtyMinus: TextView
    private lateinit var tvQtyPlus: TextView
    private lateinit var tvQtyCount: TextView
    private lateinit var btnConfirm: TextView

    fun setOnSpecConfirmedListener(listener: OnSpecConfirmedListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_spec, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivProduct = view.findViewById(R.id.ivSheetProduct)
        tvPrice = view.findViewById(R.id.tvSheetPrice)
        tvSelected = view.findViewById(R.id.tvSheetSelected)
        tvStock = view.findViewById(R.id.tvSheetStock)
        specGroupsContainer = view.findViewById(R.id.specGroupsContainer)
        tvQtyMinus = view.findViewById(R.id.tvQtyMinus)
        tvQtyPlus = view.findViewById(R.id.tvQtyPlus)
        tvQtyCount = view.findViewById(R.id.tvQtyCount)
        btnConfirm = view.findViewById(R.id.btnSheetConfirm)

        val productId = arguments?.getString(ARG_PRODUCT_ID) ?: return dismiss()
        val imageBase64 = arguments?.getString(ARG_IMAGE_BASE64)
        val basePrice = arguments?.getDouble(ARG_BASE_PRICE) ?: 0.0
        currentSkuId = arguments?.getString(ARG_CURRENT_SKU_ID)
        currentSkuLabel = arguments?.getString(ARG_CURRENT_SKU_LABEL)
        currentPrice = basePrice

        tvPrice.text = "¥$basePrice"
        com.ragagent.util.ImageUtil.loadImage(ivProduct, imageBase64)

        lifecycleScope.launch {
            loadAndParseSkus(productId)
            renderSpecGroups()
            renderQuantity()
            setupConfirmButton()
        }
    }

    // ── 数据加载 ──────────────────────────────────────

    private suspend fun loadAndParseSkus(productId: String) {
        val skus = apiService.getProductSkus(productId)
        if (skus.isEmpty()) {
            currentSkuId = null
            currentSkuLabel = "标准"
            allSkus.clear()
            specDimensions.clear()
            selectedValues.clear()
            tvSelected.text = "已选：标准"
            return
        }

        for (sku in skus) {
            val propsJson = sku["properties"]?.toString() ?: "{}"
            val props = parseProperties(propsJson)
            val price = (sku["price"] as? Double) ?: currentPrice
            val raw = sku["sku_id"]
            val skuId = (raw as? String)?.takeIf { it.isNotBlank() }
                ?: raw?.toString()?.takeIf { it.isNotBlank() }

            allSkus.add(SkuEntry(skuId = skuId, properties = props, price = price))
            for ((key, value) in props) {
                specDimensions.getOrPut(key) { mutableListOf() }.add(value)
            }
        }

        for ((key, values) in specDimensions) {
            specDimensions[key] = values.distinct().toMutableList()
        }

        if (currentSkuId != null) {
            val current = allSkus.find { it.skuId == currentSkuId }
            if (current != null) {
                selectedValues.putAll(current.properties)
                currentPrice = current.price
            }
        }
        if (selectedValues.isEmpty() && specDimensions.isNotEmpty()) {
            val first = allSkus.firstOrNull()
            if (first != null) {
                selectedValues.putAll(first.properties)
                currentSkuId = first.skuId
                currentSkuLabel = formatLabel(first.properties)
                currentPrice = first.price
            }
        }

        tvPrice.text = "¥$currentPrice"
        tvSelected.text = "已选：${currentSkuLabel ?: "标准"}"
    }

    private fun parseProperties(json: String): Map<String, String> {
        if (json.isBlank() || json == "{}") return emptyMap()
        return try {
            gson.fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
        } catch (e: Exception) { emptyMap() }
    }

    private fun formatLabel(props: Map<String, String>): String {
        if (props.isEmpty()) return "默认"
        return props.values.joinToString(" / ")
    }

    // ── 渲染 ────────────────────────────────────────

    private fun renderSpecGroups() {
        specGroupsContainer.removeAllViews()
        for ((dimName, values) in specDimensions) {
            specGroupsContainer.addView(createDimensionRow(dimName, values))
        }
    }

    private fun createDimensionRow(dimName: String, values: List<String>): LinearLayout {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16.dpToPx() }
            setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
        }
        row.addView(TextView(requireContext()).apply {
            text = dimName
            setTextColor(0xFF8B7B6B.toInt())
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        val tagsRow = FlowLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dpToPx() }
        }
        val current = selectedValues[dimName]
        for (value in values) {
            tagsRow.addView(createTagView(dimName, value, value == current))
        }
        row.addView(tagsRow)
        return row
    }

    private fun createTagView(dimName: String, value: String, selected: Boolean): TextView {
        return TextView(requireContext()).apply {
            text = value
            textSize = 13f
            setPadding(14.dpToPx(), 8.dpToPx(), 14.dpToPx(), 8.dpToPx())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            if (selected) {
                setBackgroundResource(R.drawable.bg_tag_selected)
                setTextColor(0xFFFFFFFF.toInt())
            } else if (isValueAvailable(dimName, value)) {
                setBackgroundResource(R.drawable.bg_tag)
                setTextColor(0xFF5C4A3A.toInt())
                setOnClickListener { onSpecValueSelected(dimName, value) }
            } else {
                setBackgroundResource(R.drawable.bg_tag)
                setTextColor(0xFFC4B8A8.toInt())
                alpha = 0.5f
                isEnabled = false
            }
        }
    }

    // ── 选择逻辑 ────────────────────────────────────

    private fun isValueAvailable(changedDim: String, value: String): Boolean {
        if (specDimensions.size <= 1) return true
        val test = selectedValues.toMutableMap().apply { put(changedDim, value) }
        return allSkus.any { sku -> test.all { (k, v) -> sku.properties[k] == v } }
    }

    private fun onSpecValueSelected(dimName: String, value: String) {
        if (selectedValues[dimName] == value) return
        val newSelection = selectedValues.toMutableMap().apply { put(dimName, value) }
        val matched = allSkus.find { sku -> newSelection.all { (k, v) -> sku.properties[k] == v } }
        val best = matched ?: run {
            selectedValues[dimName] = value
            findBestMatch()
        }
        if (best != null) {
            selectedValues.putAll(best.properties)
            currentSkuId = best.skuId
            currentSkuLabel = formatLabel(best.properties)
            currentPrice = best.price
        }
        tvPrice.text = "¥$currentPrice"
        tvSelected.text = "已选：${currentSkuLabel ?: "标准"}"
        renderSpecGroups()
    }

    private fun findBestMatch(): SkuEntry? {
        allSkus.find { sku -> selectedValues.all { (k, v) -> sku.properties[k] == v } }
            ?.let { return it }
        val keys = selectedValues.keys.toList()
        for (drop in 1 until keys.size) {
            val subset = selectedValues.filterKeys { it in keys.dropLast(drop) }
            allSkus.find { sku -> subset.all { (k, v) -> sku.properties[k] == v } }
                ?.let { return it }
        }
        return allSkus.firstOrNull()
    }

    // ── 数量 + 确认 ─────────────────────────────────

    private fun renderQuantity() {
        tvQtyCount.text = quantity.toString()
        tvQtyMinus.setOnClickListener {
            if (quantity > 1) { quantity--; tvQtyCount.text = quantity.toString() }
        }
        tvQtyPlus.setOnClickListener {
            if (quantity < 99) { quantity++; tvQtyCount.text = quantity.toString() }
        }
    }

    private fun setupConfirmButton() {
        btnConfirm.setOnClickListener {
            if (!AuthManager.isLoggedIn()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("请先登录").setMessage("登录后即可使用购物车功能")
                    .setPositiveButton("去登录") { _, _ ->
                        startActivity(Intent(requireContext(), LoginActivity::class.java))
                    }.setNegativeButton("取消", null).show()
                return@setOnClickListener
            }
            listener?.onSpecConfirmed(currentSkuId, currentSkuLabel, quantity)
            dismiss()
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
