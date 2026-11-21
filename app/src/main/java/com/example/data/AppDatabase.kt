package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CustomGroupEntity::class,
        MachineEntity::class,
        MaintenanceTaskEntity::class,
        TodoReminderEntity::class,
        ServiceLogEntity::class,
        SparePartUsedEntity::class,
        WarehousePartEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun machinaDao(): MachinaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE spare_parts_used ADD COLUMN partDescription TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE spare_parts_used ADD COLUMN fromWarehouse INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `warehouse_parts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uuid` TEXT NOT NULL,
                        `partCode` TEXT NOT NULL,
                        `partName` TEXT NOT NULL DEFAULT '',
                        `partDescription` TEXT NOT NULL DEFAULT '',
                        `defaultUnit` TEXT NOT NULL DEFAULT 'pcs',
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_warehouse_parts_uuid` ON `warehouse_parts` (`uuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_warehouse_parts_partCode` ON `warehouse_parts` (`partCode`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "machinalog.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
