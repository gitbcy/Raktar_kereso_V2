package com.example.raktarkereso.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface InventoryDao {

    @Insert
    suspend fun insert(item: InventoryItem): Long

    @Insert
    suspend fun insertAll(items: List<InventoryItem>)

    @Update
    suspend fun update(item: InventoryItem)

    @Delete
    suspend fun delete(item: InventoryItem)

    @Query("DELETE FROM inventory_items")
    suspend fun deleteAll()

    // Fetching everything and filtering/searching in Kotlin (see InventoryRepository)
    // is deliberate: SQLite's built-in NOCASE collation only folds plain ASCII
    // letters, so it would not reliably match Hungarian accented characters
    // (á, é, í, ó, ö, ő, ú, ü, ű). Kotlin's locale-aware lowercase() handles
    // these correctly. Inventory datasets here are small/medium, so this is safe.
    @Query("SELECT * FROM inventory_items ORDER BY productName COLLATE NOCASE ASC")
    suspend fun getAll(): List<InventoryItem>

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): InventoryItem?

    /**
     * Atomically replaces the entire table contents with [items].
     * Used by "CSV import (replace)" and "restore backup". Because this is a
     * single @Transaction, a failure partway through leaves the previous data
     * intact rather than half-deleted.
     */
    @Transaction
    suspend fun replaceAll(items: List<InventoryItem>) {
        deleteAll()
        insertAll(items)
    }
}
