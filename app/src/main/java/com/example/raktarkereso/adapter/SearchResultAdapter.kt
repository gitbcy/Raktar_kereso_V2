package com.example.raktarkereso.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.raktarkereso.R
import com.example.raktarkereso.data.InventoryItem
import com.example.raktarkereso.databinding.ItemSearchResultBinding

/**
 * Displays search results with product details, location, optional quantity
 * and notes, and per-row "Szerkesztés"/"Törlés" actions.
 */
class SearchResultAdapter(
    private val onEditClick: (InventoryItem) -> Unit,
    private val onDeleteClick: (InventoryItem) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.ResultViewHolder>() {

    private var items: List<InventoryItem> = emptyList()

    fun submitList(newItems: List<InventoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ResultViewHolder(binding, onEditClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ResultViewHolder(
        private val binding: ItemSearchResultBinding,
        private val onEditClick: (InventoryItem) -> Unit,
        private val onDeleteClick: (InventoryItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InventoryItem) {
            val context = binding.root.context

            binding.textProductName.text = item.productName
            binding.textLocation.text = "${item.warehouseName} - ${item.storageUnitName}"

            if (item.productCode.isNullOrBlank()) {
                binding.textCode.visibility = View.GONE
            } else {
                binding.textCode.visibility = View.VISIBLE
                binding.textCode.text = context.getString(R.string.result_code_prefix, item.productCode)
            }

            if (item.quantity == null) {
                binding.textQuantity.visibility = View.GONE
            } else {
                binding.textQuantity.visibility = View.VISIBLE
                val quantityDisplay = formatQuantity(item.quantity) +
                    if (!item.unit.isNullOrBlank()) " ${item.unit}" else ""
                binding.textQuantity.text = context.getString(R.string.result_quantity_prefix, quantityDisplay)
            }

            if (item.notes.isNullOrBlank()) {
                binding.textNotes.visibility = View.GONE
            } else {
                binding.textNotes.visibility = View.VISIBLE
                binding.textNotes.text = context.getString(R.string.result_notes_prefix, item.notes)
            }

            binding.buttonEdit.setOnClickListener { onEditClick(item) }
            binding.buttonDelete.setOnClickListener { onDeleteClick(item) }
        }

        // Avoids ugly trailing ".0" for whole numbers (e.g. "5" instead of "5.0").
        private fun formatQuantity(value: Double): String {
            return if (value == value.toLong().toDouble()) {
                value.toLong().toString()
            } else {
                value.toString()
            }
        }
    }
}
