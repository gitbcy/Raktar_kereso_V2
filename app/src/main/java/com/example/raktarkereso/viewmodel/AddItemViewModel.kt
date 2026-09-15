package com.example.raktarkereso.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.raktarkereso.data.InventoryItem
import com.example.raktarkereso.data.InventoryRepository
import kotlinx.coroutines.launch

/** Result of a save attempt, observed by the Add/Edit Item screen. */
sealed class SaveResult {
    object Success : SaveResult()
    object ValidationError : SaveResult()
    object InvalidQuantity : SaveResult()
    /** Possible duplicate(s) found; caller must ask the user before retrying with forceSave. */
    data class DuplicateFound(val existing: List<InventoryItem>) : SaveResult()
}

/**
 * ViewModel for the combined "add new product" / "edit product" screen.
 * When [editingId] passed to [saveItem] is non-null, an existing record is
 * updated in place (same primary key, createdAt preserved, modifiedAt bumped)
 * instead of a new row being created.
 */
class AddItemViewModel(private val repository: InventoryRepository) : ViewModel() {

    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> = _saveResult

    private val _existingItem = MutableLiveData<InventoryItem?>()
    val existingItem: LiveData<InventoryItem?> = _existingItem

    /** Loads an existing record so the edit screen can pre-fill its fields. */
    fun loadItem(id: Long) {
        viewModelScope.launch {
            _existingItem.value = repository.getById(id)
        }
    }

    fun saveItem(
        editingId: Long?,
        productName: String,
        productCode: String?,
        warehouseName: String?,
        storageUnitName: String?,
        quantityText: String?,
        unit: String?,
        notes: String?,
        forceSave: Boolean = false
    ) {
        val trimmedName = productName.trim()
        val trimmedCode = productCode?.trim()?.takeUnless { it.isEmpty() }
        val trimmedUnit = unit?.trim()?.takeUnless { it.isEmpty() }
        val trimmedNotes = notes?.trim()?.takeUnless { it.isEmpty() }

        // Required fields: product name, warehouse, storage unit.
        if (trimmedName.isEmpty() || warehouseName.isNullOrBlank() || storageUnitName.isNullOrBlank()) {
            _saveResult.value = SaveResult.ValidationError
            return
        }

        // Quantity is optional, but if provided it must be a valid non-negative number.
        val quantityTrimmed = quantityText?.trim().orEmpty()
        val quantity: Double? = if (quantityTrimmed.isEmpty()) {
            null
        } else {
            val parsed = quantityTrimmed.replace(',', '.').toDoubleOrNull()
            if (parsed == null || parsed < 0) {
                _saveResult.value = SaveResult.InvalidQuantity
                return
            }
            parsed
        }

        viewModelScope.launch {
            if (!forceSave) {
                val duplicates = repository.findPossibleDuplicates(trimmedName, trimmedCode, editingId)
                if (duplicates.isNotEmpty()) {
                    _saveResult.value = SaveResult.DuplicateFound(duplicates)
                    return@launch
                }
            }

            val now = System.currentTimeMillis()
            if (editingId == null) {
                repository.insert(
                    InventoryItem(
                        productName = trimmedName,
                        productCode = trimmedCode,
                        warehouseName = warehouseName,
                        storageUnitName = storageUnitName,
                        quantity = quantity,
                        unit = trimmedUnit,
                        notes = trimmedNotes,
                        createdAt = now,
                        modifiedAt = now
                    )
                )
            } else {
                val existing = repository.getById(editingId)
                val createdAt = existing?.createdAt ?: now
                repository.update(
                    InventoryItem(
                        id = editingId,
                        productName = trimmedName,
                        productCode = trimmedCode,
                        warehouseName = warehouseName,
                        storageUnitName = storageUnitName,
                        quantity = quantity,
                        unit = trimmedUnit,
                        notes = trimmedNotes,
                        createdAt = createdAt,
                        modifiedAt = now
                    )
                )
            }
            _saveResult.value = SaveResult.Success
        }
    }
}
