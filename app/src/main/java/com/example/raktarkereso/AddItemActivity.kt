package com.example.raktarkereso

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.raktarkereso.data.AppDatabase
import com.example.raktarkereso.data.InventoryItem
import com.example.raktarkereso.data.InventoryRepository
import com.example.raktarkereso.data.WarehouseConstants
import com.example.raktarkereso.databinding.ActivityAddItemBinding
import com.example.raktarkereso.viewmodel.AddItemViewModel
import com.example.raktarkereso.viewmodel.SaveResult
import com.example.raktarkereso.viewmodel.ViewModelFactory

/**
 * Combined "add new product" / "edit product" screen.
 * If EXTRA_ITEM_ID is present in the launching intent, the screen loads and
 * pre-fills that record and updates it in place on save; otherwise it creates
 * a new record.
 */
class AddItemActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddItemBinding
    private lateinit var viewModel: AddItemViewModel
    private var editingId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddItemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dao = AppDatabase.getDatabase(applicationContext).inventoryDao()
        val repository = InventoryRepository(dao)
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[AddItemViewModel::class.java]

        editingId = intent.getLongExtra(EXTRA_ITEM_ID, -1L).takeIf { it != -1L }

        setupDropdowns()
        setupSaveButton()
        observeViewModel()

        if (editingId != null) {
            title = getString(R.string.edit_item_title)
            binding.buttonSave.text = getString(R.string.save_changes_button)
            viewModel.loadItem(editingId!!)
        } else {
            title = getString(R.string.add_item_title)
        }
    }

    private fun setupDropdowns() {
        binding.dropdownWarehouse.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, WarehouseConstants.WAREHOUSES)
        )
        binding.dropdownStorageUnit.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, WarehouseConstants.STORAGE_UNITS)
        )
        binding.dropdownUnit.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, WarehouseConstants.UNIT_SUGGESTIONS)
        )
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener { attemptSave(forceSave = false) }
    }

    private fun attemptSave(forceSave: Boolean) {
        viewModel.saveItem(
            editingId = editingId,
            productName = binding.editProductName.text?.toString().orEmpty(),
            productCode = binding.editProductCode.text?.toString(),
            warehouseName = binding.dropdownWarehouse.text?.toString(),
            storageUnitName = binding.dropdownStorageUnit.text?.toString(),
            quantityText = binding.editQuantity.text?.toString(),
            unit = binding.dropdownUnit.text?.toString(),
            notes = binding.editNotes.text?.toString(),
            forceSave = forceSave
        )
    }

    private fun observeViewModel() {
        // Pre-fill fields once the existing record loads (edit mode only).
        viewModel.existingItem.observe(this) { item ->
            item ?: return@observe
            binding.editProductName.setText(item.productName)
            binding.editProductCode.setText(item.productCode.orEmpty())
            binding.dropdownWarehouse.setText(item.warehouseName, false)
            binding.dropdownStorageUnit.setText(item.storageUnitName, false)
            binding.editQuantity.setText(item.quantity?.toString().orEmpty())
            binding.dropdownUnit.setText(item.unit.orEmpty(), false)
            binding.editNotes.setText(item.notes.orEmpty())
        }

        viewModel.saveResult.observe(this) { result ->
            when (result) {
                is SaveResult.Success -> {
                    val message = if (editingId != null) R.string.item_updated_success else R.string.item_saved_success
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    finish()
                }
                is SaveResult.ValidationError -> {
                    Toast.makeText(this, R.string.validation_error, Toast.LENGTH_SHORT).show()
                }
                is SaveResult.InvalidQuantity -> {
                    Toast.makeText(this, R.string.invalid_quantity, Toast.LENGTH_SHORT).show()
                }
                is SaveResult.DuplicateFound -> {
                    showDuplicateWarning(result.existing)
                }
            }
        }
    }

    private fun showDuplicateWarning(existing: List<InventoryItem>) {
        val name = existing.firstOrNull()?.productName.orEmpty()
        AlertDialog.Builder(this)
            .setTitle(R.string.duplicate_warning_title)
            .setMessage(getString(R.string.duplicate_warning_message, name))
            .setPositiveButton(R.string.duplicate_confirm_positive) { _, _ -> attemptSave(forceSave = true) }
            .setNegativeButton(R.string.duplicate_confirm_negative, null)
            .show()
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
    }
}
