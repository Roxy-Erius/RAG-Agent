package com.ragagent.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ragagent.databinding.ActivityCartBinding
import com.ragagent.network.ApiService
import com.ragagent.network.ApiService.CartItemDto
import kotlinx.coroutines.launch

class CartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCartBinding
    private val apiService = ApiService()
    private lateinit var sessionId: String
    private val adapter = CartAdapter(
        onQuantityChange = { id, qty -> updateQuantity(id, qty) },
        onDelete = { id -> deleteItem(id) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionId = intent.getStringExtra("sessionId") ?: "default"

        binding.recyclerCart.layoutManager = LinearLayoutManager(this)
        binding.recyclerCart.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCheckout.setOnClickListener {
            Toast.makeText(this, "下单功能开发中", Toast.LENGTH_SHORT).show()
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
            updateTotal(items)
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
            loadCart()
        }
    }

    private fun updateTotal(items: List<CartItemDto>) {
        val total = items.sumOf { (it.productPrice ?: 0.0) * it.quantity }
        binding.tvTotal.text = "合计: ¥%.2f".format(total)
    }
}
