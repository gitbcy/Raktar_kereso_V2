package com.example.raktarkereso.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.raktarkereso.data.InventoryItem
import com.example.raktarkereso.data.InventoryRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for the main search screen.
 * Tracks the current query plus optional warehouse/storage-unit filters,
 * and whether a search/browse has actually been performed yet (so the UI
 * knows when to show "Nincs találat" versus the initial empty state).
 */
class MainViewModel(private val repository: InventoryRepository) : ViewModel() {

    private val _searchResults = MutableLiveData<List<InventoryItem>>(emptyList())
    val searchResults: LiveData<List<InventoryItem>> = _searchResults

    private val _hasSearched = MutableLiveData(false)
    val hasSearched: LiveData<Boolean> = _hasSearched

    private var currentQuery: String = ""
    private var currentWarehouseFilter: String? = null
    private var currentStorageUnitFilter: String? = null

    fun search(query: String) {
        currentQuery = query
        runSearch()
    }

    fun setWarehouseFilter(warehouse: String?) {
        currentWarehouseFilter = warehouse
        runSearch(forceShowAll = _hasSearched.value == true)
    }

    fun setStorageUnitFilter(storageUnit: String?) {
        currentStorageUnitFilter = storageUnit
        runSearch(forceShowAll = _hasSearched.value == true)
    }

    /** Explicitly requested "show everything" action (search box empty, filters optional). */
    fun showAll() {
        runSearch(forceShowAll = true)
    }

    /** Re-runs whatever the last view (search or show-all) was; used after edit/delete/import. */
    fun refresh() {
        if (_hasSearched.value == true) {
            runSearch(forceShowAll = true)
        }
    }

    fun deleteItem(item: InventoryItem) {
        viewModelScope.launch {
            repository.delete(item)
            refresh()
        }
    }

    private fun runSearch(forceShowAll: Boolean = false) {
        val trimmed = currentQuery.trim()
        val hasFilters = currentWarehouseFilter != null || currentStorageUnitFilter != null

        if (trimmed.isEmpty() && !hasFilters && !forceShowAll) {
            _searchResults.value = emptyList()
            _hasSearched.value = false
            return
        }

        viewModelScope.launch {
            val results = repository.search(trimmed, currentWarehouseFilter, currentStorageUnitFilter)
            _searchResults.value = results
            _hasSearched.value = true
        }
    }
}
