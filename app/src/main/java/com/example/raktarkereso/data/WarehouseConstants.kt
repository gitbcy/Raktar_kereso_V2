package com.example.raktarkereso.data

/**
 * Holds the fixed list of warehouses and storage units used throughout the app.
 * These are static Hungarian labels as defined by the business requirements.
 */
object WarehouseConstants {

    // Fixed list of warehouse names (Hungarian)
    val WAREHOUSES = listOf(
        "I. raktár",
        "II. raktár",
        "IV. raktár",
        "Komido"
    )

    // Fixed list of storage unit names (Hungarian).
    // The same 8 storage units exist inside every warehouse.
    val STORAGE_UNITS = listOf(
        "Jobb fent",
        "Jobb lent",
        "Középső jobb fent",
        "Középső jobb lent",
        "Középső bal fent",
        "Középső bal lent",
        "Bal fent",
        "Bal lent"
    )

    // Suggested (not enforced) unit values shown in the unit dropdown.
    val UNIT_SUGGESTIONS = listOf("kg", "liter", "db")
}

