package com.ragagent.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ragagent.R
import com.ragagent.databinding.ActivityCartBinding
import com.ragagent.network.ApiService
import kotlinx.coroutines.launch

class CartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCartBinding
    private val apiService = ApiService()
    private lateinit var sessionId: String

    private val adapter = CartAdapter(
        onQuantityChange = { id, qty -> updateQuantity(id, qty) },
        onDelete = { id -> deleteItem(id) },
        onCheckedChange = { updateTotal() }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionId = intent.getStringExtra("sessionId") ?: "default"

        binding.recyclerCart.layoutManager = LinearLayoutManager(this)
        binding.recyclerCart.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }

        binding.cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            adapter.setAllChecked(isChecked)
            binding.cbSelectAll.isChecked = adapter.isAllChecked()
        }

        binding.btnCheckout.setOnClickListener {
            val checkedIds = adapter.getCheckedIds()
            if (checkedIds.isEmpty()) {
                Toast.makeText(this, "请先选择商品", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val total = adapter.getCheckedTotal()
            val count = adapter.getCheckedCount()
            AlertDialog.Builder(this)
                .setTitle("确认支付")
                .setMessage("共 ${count} 件商品，合计 ¥%.2f".format(total))
                .setPositiveButton("确认支付") { _, _ -> checkout(checkedIds) }
                .setNegativeButton("取消", null)
                .show()
        }

        loadCart()
    }

    override fun onResume() {
        super.onResume()
        loadCart()
    }

    private fun loadCart() {
        lifecycleScope.launch {
            val items = apiService.getCart(sessionId)
            adapter.submitList(items)
            updateTotal()
            binding.cbSelectAll.isChecked = adapter.isAllChecked()
        }
    }

    private fun updateQuantity(id: Long, quantity: Int) {
        lifecycleScope.launch {
            apiService.updateCartQuantity(id, sessionId, quantity)
            loadCart()
        }
    }

    private fun deleteItem(id: Long) {
        lifecycleScope.launch {
            apiService.removeFromCart(id, sessionId)
            adapter.checkedIds.remove(id)
            loadCart()
        }
    }

    private fun updateTotal() {
        val total = adapter.getCheckedTotal()
        binding.tvTotal.text = "合计: ¥%.2f".format(total)
        val count = adapter.getCheckedCount()
        binding.btnCheckout.isEnabled = count > 0
        binding.btnCheckout.alpha = if (count > 0) 1.0f else 0.5f
    }

    private fun checkout(itemIds: List<Long>) {
        val loadingDialog = Dialog(this)
        loadingDialog.setContentView(R.layout.dialog_loading)
        loadingDialog.setCancelable(false)
        loadingDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog.show()

        lifecycleScope.launch {
            val result = apiService.checkout(sessionId, itemIds)
            loadingDialog.dismiss()
            if (result != null) {
                Toast.makeText(this@CartActivity, "支付成功！订单号: ${result.orderId}", Toast.LENGTH_LONG).show()
                // V3: 记录购买行为
                for (pid in adapter.getProductIds(itemIds)) {
                    apiService.recordBehavior(pid, "PURCHASE")
                }
                adapter.checkedIds.clear()
                loadCart()
                setResult(RESULT_OK)
            } else {
                Toast.makeText(this@CartActivity, "支付失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
