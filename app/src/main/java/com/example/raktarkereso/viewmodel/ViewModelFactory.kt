package com.example.raktarkereso.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.raktarkereso.data.InventoryRepository

/**
 * Simple factory so both ViewModels can receive the repository through
 * their constructor (no DI framework needed for an app this small).
 */
class ViewModelFactory(private val repository: InventoryRepository) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) ->
                @Suppress("UNCHECKED_CAST")
                MainViewModel(repository) as T

            modelClass.isAssignableFrom(AddItemViewModel::class.java) ->
                @Suppress("UNCHECKED_CAST")
                AddItemViewModel(repository) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
