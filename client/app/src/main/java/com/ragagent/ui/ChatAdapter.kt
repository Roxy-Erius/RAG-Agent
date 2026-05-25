package com.ragagent.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ragagent.R
import com.ragagent.databinding.ItemMessageAiBinding
import com.ragagent.databinding.ItemMessageUserBinding
import com.ragagent.databinding.ItemProductCardBinding
import com.ragagent.model.ChatMessage
import com.ragagent.model.Product

class ChatAdapter(
    private val onProductClick: (Product) -> Unit
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_AI = 1
        private const val VIEW_TYPE_PRODUCT = 2
        private const val VIEW_TYPE_LOADING = 3

        private val DiffCallback = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(old: ChatMessage, new: ChatMessage): Boolean =
                old === new

            override fun areContentsTheSame(old: ChatMessage, new: ChatMessage): Boolean =
                old == new
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ChatMessage.User -> VIEW_TYPE_USER
        is ChatMessage.Ai -> VIEW_TYPE_AI
        is ChatMessage.ProductCard -> VIEW_TYPE_PRODUCT
        is ChatMessage.Loading -> VIEW_TYPE_LOADING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val binding = ItemMessageUserBinding.inflate(inflater, parent, false)
                UserViewHolder(binding)
            }
            VIEW_TYPE_AI -> {
                val binding = ItemMessageAiBinding.inflate(inflater, parent, false)
                AiViewHolder(binding)
            }
            VIEW_TYPE_PRODUCT -> {
                val binding = ItemProductCardBinding.inflate(inflater, parent, false)
                ProductViewHolder(binding, onProductClick)
            }
            VIEW_TYPE_LOADING -> {
                val binding = ItemMessageAiBinding.inflate(inflater, parent, false)
                LoadingViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatMessage.User -> (holder as UserViewHolder).bind(item.text)
            is ChatMessage.Ai -> (holder as AiViewHolder).bind(item.text)
            is ChatMessage.ProductCard -> (holder as ProductViewHolder).bind(item.product)
            is ChatMessage.Loading -> (holder as LoadingViewHolder).bind()
        }
    }

    class UserViewHolder(private val binding: ItemMessageUserBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(text: String) {
            binding.tvMessage.text = text
        }
    }

    class AiViewHolder(private val binding: ItemMessageAiBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(text: String) {
            binding.tvMessage.text = text
        }
    }

    class LoadingViewHolder(private val binding: ItemMessageAiBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.tvMessage.text = "正在思考..."
        }
    }

    class ProductViewHolder(
        private val binding: ItemProductCardBinding,
        private val onProductClick: (Product) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentProduct: Product? = null

        init {
            binding.root.setOnClickListener {
                currentProduct?.let(onProductClick)
            }
        }

        fun bind(product: Product) {
            currentProduct = product
            binding.tvProductName.text = product.title
            binding.tvProductPrice.text = "¥${product.basePrice}"
            binding.tvProductBrand.text = product.brand

            // Glide 加载图片
            if (!product.imagePath.isNullOrBlank()) {
                com.bumptech.glide.Glide.with(binding.root.context)
                    .load(product.imagePath)
                    .placeholder(R.drawable.bg_product_placeholder)
                    .into(binding.ivProductImage)
            }
        }
    }
}
