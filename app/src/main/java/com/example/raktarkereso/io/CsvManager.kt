package com.example.raktarkereso.io

import com.example.raktarkereso.data.InventoryItem
import com.example.raktarkereso.data.WarehouseConstants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hand-rolled CSV reader/writer (no external dependency). Handles standard
 * RFC 4180 quoting so notes/fields containing commas, quotes, or newlines
 * round-trip correctly, and always writes/reads UTF-8 so Hungarian accented
 * characters are preserved.
 */
object CsvManager {

    private const val DELIMITER = ','

    // Column order for both export and import. "ID" is exported for reference
    // but ignored on import — a fresh id is always assigned to imported rows.
    private val HEADER = listOf(
        "ID", "Termék neve", "Termékkód", "Raktár", "Tároló egység",
        "Mennyiség", "Mértékegység", "Megjegyzés", "Létrehozva", "Módosítva"
    )

    // Numeric-only date format: locale-independent regardless of the Locale passed,
    // but Locale.ROOT is used explicitly for clarity and to avoid any ambiguity.
    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    fun toCsv(items: List<InventoryItem>): String {
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(DELIMITER.toString()) { escape(it) }).append("\r\n")
        for (item in items) {
            val row = listOf(
                item.id.toString(),
                item.productName,
                item.productCode.orEmpty(),
                item.warehouseName,
                item.storageUnitName,
                item.quantity?.toString().orEmpty(),
                item.unit.orEmpty(),
                item.notes.orEmpty(),
                DATE_FORMAT.format(Date(item.createdAt)),
                DATE_FORMAT.format(Date(item.modifiedAt))
            )
            sb.append(row.joinToString(DELIMITER.toString()) { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun escape(value: String): String {
        return if (value.contains(DELIMITER) || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    /** One successfully parsed and validated CSV row, ready to become an [InventoryItem]. */
    data class ParsedRow(
        val rowNumber: Int,
        val productName: String,
        val productCode: String?,
        val warehouseName: String,
        val storageUnitName: String,
        val quantity: Double?,
        val unit: String?,
        val notes: String?,
        val createdAt: Long,
        val modifiedAt: Long
    )

    sealed class ParseResult {
        data class Success(val rows: List<ParsedRow>) : ParseResult()
        /** rowNumber (1-based, header counts as row 1) paired with a Hungarian reason. */
        data class Failure(val invalidRows: List<Pair<Int, String>>) : ParseResult()
    }

    /**
     * Parses and fully validates [content]. Per the "never corrupt existing
     * data" requirement, this either returns ALL rows successfully parsed, or
     * a Failure listing every invalid row — callers must not apply a partial
     * import from a Failure result.
     */
    fun parseCsv(content: String): ParseResult {
        val lines = splitCsvLines(content)
        if (lines.isEmpty()) {
            return ParseResult.Failure(listOf(0 to "A fájl üres."))
        }

        val dataLines = lines.drop(1) // skip header row
        val validRows = mutableListOf<ParsedRow>()
        val invalidRows = mutableListOf<Pair<Int, String>>()

        dataLines.forEachIndexed { index, line ->
            val rowNumber = index + 2 // header is row 1
            if (line.isBlank()) return@forEachIndexed

            val fields = parseCsvLine(line)
            val productName = fields.getOrNull(1)?.trim().orEmpty()
            val productCode = fields.getOrNull(2)?.trim()?.takeUnless { it.isEmpty() }
            val warehouseName = fields.getOrNull(3)?.trim().orEmpty()
            val storageUnitName = fields.getOrNull(4)?.trim().orEmpty()
            val quantityRaw = fields.getOrNull(5)?.trim().orEmpty()
            val unit = fields.getOrNull(6)?.trim()?.takeUnless { it.isEmpty() }
            val notes = fields.getOrNull(7)?.trim()?.takeUnless { it.isEmpty() }
            val createdRaw = fields.getOrNull(8)?.trim().orEmpty()
            val modifiedRaw = fields.getOrNull(9)?.trim().orEmpty()

            if (productName.isEmpty()) {
                invalidRows.add(rowNumber to "Hiányzó termék név.")
                return@forEachIndexed
            }
            if (warehouseName !in WarehouseConstants.WAREHOUSES) {
                invalidRows.add(rowNumber to "Érvénytelen raktár: \"$warehouseName\".")
                return@forEachIndexed
            }
            if (storageUnitName !in WarehouseConstants.STORAGE_UNITS) {
                invalidRows.add(rowNumber to "Érvénytelen tároló egység: \"$storageUnitName\".")
                return@forEachIndexed
            }

            val quantity: Double? = if (quantityRaw.isEmpty()) {
                null
            } else {
                val parsed = quantityRaw.replace(',', '.').toDoubleOrNull()
                if (parsed == null) {
                    invalidRows.add(rowNumber to "Érvénytelen mennyiség: \"$quantityRaw\".")
                    return@forEachIndexed
                }
                parsed
            }

            validRows.add(
                ParsedRow(
                    rowNumber = rowNumber,
                    productName = productName,
                    productCode = productCode,
                    warehouseName = warehouseName,
                    storageUnitName = storageUnitName,
                    quantity = quantity,
                    unit = unit,
                    notes = notes,
                    createdAt = parseDateOrNow(createdRaw),
                    modifiedAt = parseDateOrNow(modifiedRaw)
                )
            )
        }

        return if (invalidRows.isEmpty()) ParseResult.Success(validRows) else ParseResult.Failure(invalidRows)
    }

    private fun parseDateOrNow(raw: String): Long {
        if (raw.isEmpty()) return System.currentTimeMillis()
        return try {
            DATE_FORMAT.parse(raw)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /** Splits raw file content into logical CSV lines, respecting quoted multi-line fields. */
    private fun splitCsvLines(content: String): List<String> {
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false
        var i = 0
        while (i < content.length) {
            val c = content[i]
            when {
                c == '"' -> {
                    insideQuotes = !insideQuotes
                    current.append(c)
                }
                (c == '\n' || c == '\r') && !insideQuotes -> {
                    lines.add(current.toString())
                    current.clear()
                    if (c == '\r' && i + 1 < content.length && content[i + 1] == '\n') {
                        i++ // treat \r\n as a single line break
                    }
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.filter { it.isNotEmpty() }
    }

    /** Parses a single CSV line into fields, honoring quoted values and escaped ("") quotes. */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (insideQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        insideQuotes = !insideQuotes
                    }
                }
                c == DELIMITER && !insideQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
