package com.example.raktarkereso.io

import com.example.raktarkereso.data.InventoryItem
import com.example.raktarkereso.data.WarehouseConstants
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Full backup/restore as portable JSON. Uses org.json, which ships with the
 * Android platform, so no extra Gradle dependency is introduced.
 */
object BackupManager {

    private const val KEY_BACKUP_VERSION = "backupVersion"
    private const val KEY_ITEMS = "items"
    private const val CURRENT_BACKUP_VERSION = 1

    fun toJson(items: List<InventoryItem>): String {
        val root = JSONObject()
        root.put(KEY_BACKUP_VERSION, CURRENT_BACKUP_VERSION)

        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("productName", item.productName)
            obj.put("productCode", item.productCode ?: JSONObject.NULL)
            obj.put("warehouseName", item.warehouseName)
            obj.put("storageUnitName", item.storageUnitName)
            obj.put("quantity", item.quantity ?: JSONObject.NULL)
            obj.put("unit", item.unit ?: JSONObject.NULL)
            obj.put("notes", item.notes ?: JSONObject.NULL)
            obj.put("createdAt", item.createdAt)
            obj.put("modifiedAt", item.modifiedAt)
            array.put(obj)
        }
        root.put(KEY_ITEMS, array)
        return root.toString(2)
    }

    sealed class RestoreParseResult {
        data class Success(val items: List<InventoryItem>) : RestoreParseResult()
        /** Hungarian, user-facing reason the backup could not be used. */
        data class Failure(val reason: String) : RestoreParseResult()
    }

    /**
     * Parses and fully validates a backup file's contents before anything is
     * ever written to the database. On any structural problem, returns
     * Failure and the caller must leave the existing database untouched.
     */
    fun parseJson(content: String): RestoreParseResult {
        return try {
            val root = JSONObject(content)
            val array = root.optJSONArray(KEY_ITEMS)
                ?: return RestoreParseResult.Failure("A mentés nem tartalmaz érvényes adatlistát.")

            val items = mutableListOf<InventoryItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i)
                    ?: return RestoreParseResult.Failure("Érvénytelen bejegyzés a mentés ${i + 1}. helyén.")

                val productName = obj.optString("productName").trim()
                val warehouseName = obj.optString("warehouseName").trim()
                val storageUnitName = obj.optString("storageUnitName").trim()

                if (productName.isEmpty()) {
                    return RestoreParseResult.Failure("Hiányzó termék név a mentés ${i + 1}. bejegyzésénél.")
                }
                if (warehouseName !in WarehouseConstants.WAREHOUSES) {
                    return RestoreParseResult.Failure(
                        "Érvénytelen raktár a mentés ${i + 1}. bejegyzésénél: \"$warehouseName\"."
                    )
                }
                if (storageUnitName !in WarehouseConstants.STORAGE_UNITS) {
                    return RestoreParseResult.Failure(
                        "Érvénytelen tároló egység a mentés ${i + 1}. bejegyzésénél: \"$storageUnitName\"."
                    )
                }

                val productCode = if (obj.isNull("productCode")) null else
                    obj.optString("productCode").takeUnless { it.isEmpty() }
                val quantity = if (obj.isNull("quantity")) null else
                    obj.optDouble("quantity").takeUnless { it.isNaN() }
                val unit = if (obj.isNull("unit")) null else
                    obj.optString("unit").takeUnless { it.isEmpty() }
                val notes = if (obj.isNull("notes")) null else
                    obj.optString("notes").takeUnless { it.isEmpty() }
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val modifiedAt = obj.optLong("modifiedAt", createdAt)

                items.add(
                    InventoryItem(
                        id = 0, // a fresh id is assigned on insert during restore
                        productName = productName,
                        productCode = productCode,
                        warehouseName = warehouseName,
                        storageUnitName = storageUnitName,
                        quantity = quantity,
                        unit = unit,
                        notes = notes,
                        createdAt = createdAt,
                        modifiedAt = modifiedAt
                    )
                )
            }
            RestoreParseResult.Success(items)
        } catch (e: JSONException) {
            RestoreParseResult.Failure("A fájl nem érvényes biztonsági mentés (hibás JSON formátum).")
        }
    }
}
