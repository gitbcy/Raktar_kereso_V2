package com.example.raktarkereso.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Single Room database for the app. Fully local (SQLite under the hood),
 * so the app works with no internet connection at all.
 *
 * Version history:
 *  - v1: id, productName, warehouseName, storageUnitName, createdAt
 *  - v2: adds productCode, quantity, unit, notes, modifiedAt (this update)
 *
 * IMPORTANT: this app never uses fallbackToDestructiveMigration(). Any schema
 * change ships with an explicit Migration so existing on-device inventory
 * data survives app updates.
 */
@Database(entities = [InventoryItem::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun inventoryDao(): InventoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2: adds the new optional columns introduced by this update.
         * All existing rows are preserved; modifiedAt is backfilled from
         * createdAt for rows that predate this column.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN productCode TEXT")
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN quantity REAL")
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN unit TEXT")
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN notes TEXT")
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN modifiedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE inventory_items SET modifiedAt = createdAt WHERE modifiedAt = 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "raktar_kereso_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
