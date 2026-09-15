package com.example.raktarkereso

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.raktarkereso.data.AppDatabase
import com.example.raktarkereso.data.InventoryItem
import com.example.raktarkereso.data.InventoryRepository
import com.example.raktarkereso.databinding.ActivitySettingsBinding
import com.example.raktarkereso.io.BackupManager
import com.example.raktarkereso.io.CsvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * "Adatkezelés" (data management) screen: CSV export/import, full JSON
 * backup/restore, show-all shortcut, and delete-all — all using the Storage
 * Access Framework so no storage permissions are required.
 *
 * This screen talks to the repository directly (via lifecycleScope) rather
 * than through its own ViewModel: every action here is a short, one-shot,
 * user-initiated file operation with no state that needs to survive
 * configuration changes, so the extra ViewModel layer would add complexity
 * without benefit. Database work still always runs on Dispatchers.IO.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: InventoryRepository

    private val exportCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { exportCsvTo(it) } }

    private val importCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { promptImportMode(it) } }

    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { backupTo(it) } }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { restoreFrom(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.settings_title)

        val dao = AppDatabase.getDatabase(applicationContext).inventoryDao()
        repository = InventoryRepository(dao)

        binding.buttonShowAll.setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_SHOW_ALL, true))
            finish()
        }
        binding.buttonExportCsv.setOnClickListener {
            exportCsvLauncher.launch("raktar_export.csv")
        }
        binding.buttonImportCsv.setOnClickListener {
            importCsvLauncher.launch(arrayOf("*/*"))
        }
        binding.buttonBackup.setOnClickListener {
            backupLauncher.launch("raktar_backup.json")
        }
        binding.buttonRestore.setOnClickListener {
            restoreLauncher.launch(arrayOf("*/*"))
        }
        binding.buttonDeleteAll.setOnClickListener {
            confirmDeleteAll()
        }
    }

    // ---- CSV export ----

    private fun exportCsvTo(uri: Uri) {
        lifecycleScope.launch {
            try {
                val csv = withContext(Dispatchers.IO) {
                    CsvManager.toCsv(repository.getAll())
                }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("Nem sikerült megnyitni a fájlt írásra.")
                }
                Toast.makeText(this@SettingsActivity, R.string.csv_export_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.csv_export_error, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---- CSV import ----

    private fun promptImportMode(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.import_mode_title)
            .setMessage(R.string.import_mode_message)
            .setPositiveButton(R.string.import_mode_append) { _, _ -> importCsvFrom(uri, replace = false) }
            .setNegativeButton(R.string.import_mode_replace) { _, _ -> importCsvFrom(uri, replace = true) }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun importCsvFrom(uri: Uri, replace: Boolean) {
        lifecycleScope.launch {
            try {
                val content = readTextFrom(uri)
                when (val result = CsvManager.parseCsv(content)) {
                    is CsvManager.ParseResult.Failure -> {
                        val details = result.invalidRows.joinToString("\n") { (row, reason) -> "Sor $row: $reason" }
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle(R.string.import_error_title)
                            .setMessage(getString(R.string.import_invalid_rows, details))
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                    is CsvManager.ParseResult.Success -> {
                        val newItems = result.rows.map {
                            InventoryItem(
                                productName = it.productName,
                                productCode = it.productCode,
                                warehouseName = it.warehouseName,
                                storageUnitName = it.storageUnitName,
                                quantity = it.quantity,
                                unit = it.unit,
                                notes = it.notes,
                                createdAt = it.createdAt,
                                modifiedAt = it.modifiedAt
                            )
                        }
                        withContext(Dispatchers.IO) {
                            if (replace) repository.replaceAll(newItems) else repository.insertAll(newItems)
                        }
                        Toast.makeText(this@SettingsActivity, R.string.csv_import_success, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Parsing/DB failed: nothing was written (parse-then-write is atomic per branch above),
                // so existing data remains exactly as it was.
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.csv_import_error, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---- Full backup ----

    private fun backupTo(uri: Uri) {
        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    BackupManager.toJson(repository.getAll())
                }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("Nem sikerült megnyitni a fájlt írásra.")
                }
                Toast.makeText(this@SettingsActivity, R.string.backup_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.backup_error, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---- Restore backup ----

    private fun restoreFrom(uri: Uri) {
        lifecycleScope.launch {
            try {
                val content = readTextFrom(uri)
                when (val result = BackupManager.parseJson(content)) {
                    is BackupManager.RestoreParseResult.Failure -> {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle(R.string.restore_error_title)
                            .setMessage(result.reason)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                    is BackupManager.RestoreParseResult.Success -> {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle(R.string.restore_confirm_title)
                            .setMessage(R.string.restore_confirm_message)
                            .setPositiveButton(R.string.restore_confirm_positive) { _, _ ->
                                lifecycleScope.launch {
                                    withContext(Dispatchers.IO) { repository.replaceAll(result.items) }
                                    Toast.makeText(this@SettingsActivity, R.string.restore_success, Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.restore_error, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---- Delete all ----

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_all_confirm_title)
            .setMessage(R.string.delete_all_confirm_message)
            .setPositiveButton(R.string.delete_all_confirm_positive) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repository.deleteAll() }
                    Toast.makeText(this@SettingsActivity, R.string.delete_all_success, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private suspend fun readTextFrom(uri: Uri): String = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IOException("Nem sikerült megnyitni a fájlt olvasásra.")
    }

    companion object {
        const val EXTRA_SHOW_ALL = "extra_show_all"
    }
}
