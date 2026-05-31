package com.ragagent.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ragagent.databinding.ItemCartProductBinding
import com.ragagent.network.ApiService.CartItemDto

class CartAdapter(
    private val onQuantityChange: (Long, Int) -> Unit,
    private val onDelete: (Long) -> Unit
) : RecyclerView.Adapter<CartAdapter.ViewHolder>() {

    private var items: List<CartItemDto> = emptyList()

    fun submitList(list: List<CartItemDto>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCartProductBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemCartProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CartItemDto) {
            binding.tvCartProductName.text = item.productTitle ?: item.productId
            binding.tvCartProductPrice.text = "¥${item.productPrice ?: 0.0}"
            binding.tvQuantity.text = item.quantity.toString()

            binding.btnPlus.setOnClickListener {
                onQuantityChange(item.id, item.quantity + 1)
            }
            binding.btnMinus.setOnClickListener {
                val newQty = item.quantity - 1
                if (newQty > 0) onQuantityChange(item.id, newQty)
                else onDelete(item.id)
            }
            binding.btnDelete.setOnClickListener { onDelete(item.id) }
        }
    }
}
