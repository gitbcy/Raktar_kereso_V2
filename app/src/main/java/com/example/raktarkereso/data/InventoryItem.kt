package com.example.raktarkereso.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single product stored in a specific
 * warehouse and storage unit. Multiple items can share the same
 * warehouse/storage-unit combination (a storage unit can hold many products).
 *
 * id is a stable auto-generated primary key: it is never reused or
 * reassigned by edits, so editing a record always updates the same row.
 */
@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Name or ID of the product, exactly as entered by the user (required)
    val productName: String,

    // Optional separate product/SKU code, independent of productName
    val productCode: String? = null,

    // e.g. "II. raktár" (required)
    val warehouseName: String,

    // e.g. "Középső jobb fent" (required)
    val storageUnitName: String,

    // Optional stock quantity, e.g. 12.5
    val quantity: Double? = null,

    // Optional unit for the quantity, e.g. "kg", "liter", "db"
    val unit: String? = null,

    // Optional free-text notes
    val notes: String? = null,

    // Set once, at insert time; never changed afterward
    val createdAt: Long = System.currentTimeMillis(),

    // Updated every time the record is edited
    val modifiedAt: Long = System.currentTimeMillis()
)
