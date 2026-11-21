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
        WarehousePartEntity::class,
        EngineerEntity::class
    ],
    version = 4,
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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE maintenance_tasks ADD COLUMN assistantEngineers TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE service_logs ADD COLUMN outcome TEXT NOT NULL DEFAULT 'DONE'")
                db.execSQL("ALTER TABLE service_logs ADD COLUMN rejectionReason TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE service_logs ADD COLUMN assistantEngineers TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `engineers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uuid` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `employeeCode` TEXT NOT NULL DEFAULT '',
                        `role` TEXT NOT NULL DEFAULT 'Maintenance Engineer',
                        `shift` TEXT NOT NULL DEFAULT 'Day Shift',
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_engineers_uuid` ON `engineers` (`uuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_engineers_name` ON `engineers` (`name`)")

                // Backfill existing PIC names into engineers table so PIC pickers remain populated
                val existingNames = linkedSetOf<String>()
                fun collectFrom(query: String) {
                    db.query(query).use { cursor ->
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(0)?.trim().orEmpty()
                            if (name.isNotEmpty()) existingNames += name
                        }
                    }
                }
                collectFrom("SELECT DISTINCT personInCharge FROM maintenance_tasks")
                collectFrom("SELECT DISTINCT personInCharge FROM todo_reminders")
                collectFrom("SELECT DISTINCT personInCharge FROM service_logs")

                val now = System.currentTimeMillis()
                existingNames.forEachIndexed { idx, name ->
                    val code = "ENG-${(idx + 1).toString().padStart(2, '0')}"
                    val uuid = java.util.UUID.randomUUID().toString()
                    db.execSQL(
                        "INSERT INTO engineers (uuid, name, employeeCode, role, shift, updatedAtMillis) VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf<Any>(uuid, name, code, "Maintenance Engineer", "Day Shift", now)
                    )
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "machinalog.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
