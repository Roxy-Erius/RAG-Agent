package com.ragagent.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ragagent.databinding.ItemConversationBinding
import com.ragagent.network.ApiService.ConversationDto

class HistoryAdapter(
    private val items: List<ConversationDto>,
    private val onClick: (ConversationDto) -> Unit,
    private val onDelete: (ConversationDto) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(private val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: ConversationDto,
            onClick: (ConversationDto) -> Unit,
            onDelete: (ConversationDto) -> Unit
        ) {
            binding.tvTitle.text = item.title ?: "新对话"
            binding.tvUpdatedAt.text = item.updatedAt?.take(16)?.replace("T", " ") ?: ""
            binding.root.setOnClickListener { onClick(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onClick, onDelete)
    }

    override fun getItemCount(): Int = items.size
}
