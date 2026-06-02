package com.ragagent.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ragagent.databinding.ItemCartProductBinding
import com.ragagent.network.ApiService.CartItemDto

class CartAdapter(
    private val onQuantityChange: (Long, Int) -> Unit,
    private val onDelete: (Long) -> Unit,
    private val onCheckedChange: () -> Unit
) : RecyclerView.Adapter<CartAdapter.ViewHolder>() {

    private var items: List<CartItemDto> = emptyList()
    val checkedIds: MutableSet<Long> = mutableSetOf()

    fun submitList(list: List<CartItemDto>) {
        items = list
        notifyDataSetChanged()
    }

    fun getCheckedIds(): List<Long> = checkedIds.toList()

    fun setAllChecked(checked: Boolean) {
        if (checked) {
            checkedIds.addAll(items.map { it.id })
        } else {
            checkedIds.clear()
        }
        notifyDataSetChanged()
        onCheckedChange()
    }

    fun isAllChecked(): Boolean = items.isNotEmpty() && checkedIds.size == items.size

    fun getCheckedTotal(): Double = items
        .filter { checkedIds.contains(it.id) }
        .sumOf { (it.productPrice ?: 0.0) * it.quantity }

    fun getCheckedCount(): Int = checkedIds.size

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
            binding.cbSelect.isChecked = checkedIds.contains(item.id)

            binding.cbSelect.setOnCheckedChangeListener(null)  // avoid trigger during bind
            binding.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) checkedIds.add(item.id) else checkedIds.remove(item.id)
                onCheckedChange()
            }

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
