package com.example.raktarkereso

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.raktarkereso.adapter.SearchResultAdapter
import com.example.raktarkereso.data.AppDatabase
import com.example.raktarkereso.data.InventoryItem
import com.example.raktarkereso.data.InventoryRepository
import com.example.raktarkereso.data.WarehouseConstants
import com.example.raktarkereso.databinding.ActivityMainBinding
import com.example.raktarkereso.viewmodel.MainViewModel
import com.example.raktarkereso.viewmodel.ViewModelFactory

/**
 * Main screen: search bar + warehouse/storage-unit filters at the top,
 * live results list (with per-row edit/delete) below, and buttons to add a
 * new product or open the data-management ("Beállítások") screen.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: SearchResultAdapter

    // "Összes raktár" / "Összes tároló egység" occupy index 0 of their spinners and mean "no filter".
    private val warehouseFilterOptions: List<String> by lazy {
        listOf(getString(R.string.filter_all_warehouses)) + WarehouseConstants.WAREHOUSES
    }
    private val storageUnitFilterOptions: List<String> by lazy {
        listOf(getString(R.string.filter_all_storage_units)) + WarehouseConstants.STORAGE_UNITS
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK &&
            result.data?.getBooleanExtra(SettingsActivity.EXTRA_SHOW_ALL, false) == true
        ) {
            resetFiltersAndShowAll()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Manual dependency setup: DB -> Repository -> ViewModel.
        val dao = AppDatabase.getDatabase(applicationContext).inventoryDao()
        val repository = InventoryRepository(dao)
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        adapter = SearchResultAdapter(
            onEditClick = { item -> openEditScreen(item) },
            onDeleteClick = { item -> confirmDelete(item) }
        )

        setupRecyclerView()
        setupSearch()
        setupFilters()
        setupButtons()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        binding.recyclerResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerResults.adapter = adapter
    }

    private fun setupSearch() {
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupFilters() {
        binding.spinnerWarehouseFilter.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, warehouseFilterOptions
        )
        binding.spinnerStorageUnitFilter.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, storageUnitFilterOptions
        )

        binding.spinnerWarehouseFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setWarehouseFilter(if (position == 0) null else warehouseFilterOptions[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spinnerStorageUnitFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setStorageUnitFilter(if (position == 0) null else storageUnitFilterOptions[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.buttonShowAll.setOnClickListener {
            resetFiltersAndShowAll()
        }
        binding.buttonAddItem.setOnClickListener {
            startActivity(Intent(this, AddItemActivity::class.java))
        }
        binding.buttonSettings.setOnClickListener {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun resetFiltersAndShowAll() {
        binding.editSearch.setText("")
        binding.spinnerWarehouseFilter.setSelection(0)
        binding.spinnerStorageUnitFilter.setSelection(0)
        viewModel.showAll()
    }

    private fun openEditScreen(item: InventoryItem) {
        val intent = Intent(this, AddItemActivity::class.java)
        intent.putExtra(AddItemActivity.EXTRA_ITEM_ID, item.id)
        startActivity(intent)
    }

    private fun confirmDelete(item: InventoryItem) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_confirm_message)
            .setPositiveButton(R.string.delete_confirm_positive) { _, _ ->
                viewModel.deleteItem(item)
                Toast.makeText(this, R.string.item_deleted_success, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.delete_confirm_negative, null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.searchResults.observe(this) { results ->
            adapter.submitList(results)
            binding.recyclerResults.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.hasSearched.observe(this) { hasSearched ->
            val currentResults = viewModel.searchResults.value.orEmpty()
            val showNoResults = hasSearched && currentResults.isEmpty()
            binding.textNoResults.visibility = if (showNoResults) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-run whatever was on screen (search, filters, or show-all) so that
        // edits/deletes/imports made elsewhere are reflected immediately.
        viewModel.refresh()
    }
}
