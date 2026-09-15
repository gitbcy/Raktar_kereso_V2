package com.example.raktarkereso.data

/**
 * Repository layer: the single source of truth for inventory data.
 * ViewModels and utility screens (Settings) talk to this class instead of
 * touching the DAO directly, which keeps the persistence mechanism
 * (Room/SQLite) swappable and keeps search/duplicate logic in one place.
 */
class InventoryRepository(private val dao: InventoryDao) {

    suspend fun insert(item: InventoryItem): Long = dao.insert(item)

    suspend fun insertAll(items: List<InventoryItem>) = dao.insertAll(items)

    suspend fun update(item: InventoryItem) = dao.update(item)

    suspend fun delete(item: InventoryItem) = dao.delete(item)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun getAll(): List<InventoryItem> = dao.getAll()

    suspend fun getById(id: Long): InventoryItem? = dao.getById(id)

    /** Atomically replaces all data — used by CSV import (replace mode) and restore. */
    suspend fun replaceAll(items: List<InventoryItem>) = dao.replaceAll(items)

    /**
     * Case-insensitive, Hungarian-accent-aware search across product name and
     * product code, with optional exact-match warehouse/storage-unit filters.
     * Pass an empty [query] with a non-null filter to just filter by location;
     * pass an empty query and null filters from the "show all" action.
     */
    suspend fun search(query: String, warehouse: String?, storageUnit: String?): List<InventoryItem> {
        val all = dao.getAll()
        val q = query.trim().lowercase(HU_LOCALE)
        return all.filter { item ->
            val matchesQuery = q.isEmpty() ||
                item.productName.lowercase(HU_LOCALE).contains(q) ||
                (item.productCode?.lowercase(HU_LOCALE)?.contains(q) == true)
            val matchesWarehouse = warehouse == null || item.warehouseName == warehouse
            val matchesStorageUnit = storageUnit == null || item.storageUnitName == storageUnit
            matchesQuery && matchesWarehouse && matchesStorageUnit
        }.sortedBy { it.productName.lowercase(HU_LOCALE) }
    }

    /**
     * Finds existing records that look like the same product (same product
     * name, or same non-blank product code), anywhere in any warehouse.
     * Used to warn the user before creating a possible duplicate — this never
     * blocks the save, since the same product legitimately can exist in
     * multiple locations.
     */
    suspend fun findPossibleDuplicates(
        productName: String,
        productCode: String?,
        excludeId: Long?
    ): List<InventoryItem> {
        val all = dao.getAll()
        val nameLower = productName.trim().lowercase(HU_LOCALE)
        val codeLower = productCode?.trim()?.takeUnless { it.isEmpty() }?.lowercase(HU_LOCALE)

        return all.filter { item ->
            if (excludeId != null && item.id == excludeId) return@filter false
            val nameMatches = item.productName.lowercase(HU_LOCALE) == nameLower
            val codeMatches = codeLower != null && item.productCode?.lowercase(HU_LOCALE) == codeLower
            nameMatches || codeMatches
        }
    }
}
