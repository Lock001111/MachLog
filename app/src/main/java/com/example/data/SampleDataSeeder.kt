package com.example.data

import java.util.concurrent.TimeUnit

object SampleDataSeeder {

    suspend fun seedIfNeeded(dao: MachinaDao, prefs: AppPreferences) {
        val snapshot = prefs.getSnapshot()
        if (snapshot.sampleDataSeeded) {
            // Backfill sample warehouse parts catalog once for devices upgrading from v2 to v3
            if (dao.getAllWarehousePartsSync().isEmpty() &&
                dao.getAllServiceLogsWithPartsSync().any { item -> item.parts.any { it.partCode == "AC-2901-1095" } }
            ) {
                seedWarehouseCatalog(dao, System.currentTimeMillis())
            }
            return
        }
        if (dao.getAllMachinesSync().isNotEmpty()) {
            prefs.setSampleDataSeeded(true)
            return
        }
        insertSampleDataset(dao)
        prefs.setSampleDataSeeded(true)
    }

    suspend fun resetWithSampleDataset(dao: MachinaDao, prefs: AppPreferences) {
        dao.clearAllData()
        insertSampleDataset(dao)
        prefs.setSampleDataSeeded(true)
    }

    private suspend fun insertSampleDataset(dao: MachinaDao) {
        val now = System.currentTimeMillis()
        val hour = TimeUnit.HOURS.toMillis(1)
        val day = TimeUnit.DAYS.toMillis(1)

        // 1. Custom Groups
        val gCncId = dao.insertGroup(
            CustomGroupEntity(
                name = "CNC & Milling Bay",
                scope = GroupScope.ALL,
                colorKey = "COBALT",
                updatedAtMillis = now
            )
        )
        val gPackId = dao.insertGroup(
            CustomGroupEntity(
                name = "Packaging Line A",
                scope = GroupScope.ALL,
                colorKey = "TEAL",
                updatedAtMillis = now
            )
        )
        val gUtilId = dao.insertGroup(
            CustomGroupEntity(
                name = "Utilities & Air",
                scope = GroupScope.ALL,
                colorKey = "AMBER",
                updatedAtMillis = now
            )
        )
        val gSafetyId = dao.insertGroup(
            CustomGroupEntity(
                name = "Safety & Calibration",
                scope = GroupScope.TASK,
                colorKey = "CRIMSON",
                updatedAtMillis = now
            )
        )

        // 2. Machines
        val m1 = dao.insertMachine(
            MachineEntity(
                groupId = gCncId,
                assetCode = "MCH-101",
                name = "Haas VF-3 CNC Vertical Mill",
                locationZone = "Bay 1 · Precision Machining",
                modelInfo = "Haas VF-3SS · SN 1184290",
                status = MachineStatus.OPERATIONAL,
                notes = "Primary 3-axis aluminum housing mill. Coolant tank 280L.",
                updatedAtMillis = now
            )
        )
        val m2 = dao.insertMachine(
            MachineEntity(
                groupId = gCncId,
                assetCode = "MCH-204",
                name = "Bosch Rexroth 120T Hydraulic Press",
                locationZone = "Bay 2 · Forming Cell",
                modelInfo = "Rexroth HPR-120 · SN 90421-B",
                status = MachineStatus.MAINTENANCE_DUE,
                notes = "Check relief valve pressure during shift handover.",
                updatedAtMillis = now
            )
        )
        val m3 = dao.insertMachine(
            MachineEntity(
                groupId = gUtilId,
                assetCode = "MCH-302",
                name = "Atlas Copco GA-55 Air Compressor",
                locationZone = "Utility Room North",
                modelInfo = "GA-55 VSD+ · SN AC-55910",
                status = MachineStatus.MALFUNCTION,
                notes = "Intermittent discharge temp spike above 98C reported on Line 2.",
                updatedAtMillis = now
            )
        )
        val m4 = dao.insertMachine(
            MachineEntity(
                groupId = gPackId,
                assetCode = "MCH-410",
                name = "Multivac R-245 Thermoformer Sealer",
                locationZone = "Line A · Clean Packaging",
                modelInfo = "Multivac R-245 · SN MV-24588",
                status = MachineStatus.OPERATIONAL,
                notes = "Keep PTFE jaw tape stocked at Line A cabinet.",
                updatedAtMillis = now
            )
        )
        val m5 = dao.insertMachine(
            MachineEntity(
                groupId = gPackId,
                assetCode = "MCH-508",
                name = "Fanuc M-20iD Palletizing Robot",
                locationZone = "Line A · End-of-Line",
                modelInfo = "Fanuc M-20iD/25 · SN FN-88201",
                status = MachineStatus.OPERATIONAL,
                notes = "6-axis gripper cell; annual harmonic drive grease schedule.",
                updatedAtMillis = now
            )
        )

        // 3. Maintenance Tasks (Daily, Weekly, Monthly, Yearly)
        val t1 = dao.insertTask(
            MaintenanceTaskEntity(
                machineId = m1,
                groupId = gCncId,
                title = "Coolant Brix & Sump Level Check",
                description = "Verify refractometer reading 7-9%, top up coolant, clear chip auger.",
                cadence = Cadence.DAILY,
                deadlineMillis = now + 3 * hour,
                estimatedMinutes = 20,
                personInCharge = "Elena Vance",
                reminderEnabled = true,
                reminderLeadHours = 4,
                lastCompletedMillis = now - day,
                updatedAtMillis = now
            )
        )
        val t2 = dao.insertTask(
            MaintenanceTaskEntity(
                machineId = m4,
                groupId = gPackId,
                title = "Heat Seal Bar Surface & Vacuum Check",
                description = "Inspect PTFE strip for wear and verify -0.85 bar sealing vacuum.",
                cadence = Cadence.DAILY,
                deadlineMillis = now - 2 * hour, // Overdue
                estimatedMinutes = 25,
                personInCharge = "Marcus Chen",
                reminderEnabled = true,
                reminderLeadHours = 2,
                lastCompletedMillis = now - 2 * day,
                updatedAtMillis = now
            )
        )
        val t3 = dao.insertTask(
            MaintenanceTaskEntity(
                machineId = m2,
                groupId = gSafetyId,
                title = "Light Curtain & E-Stop Safety Test",
                description = "Test dual-channel safety relay response and optical curtain alignment.",
                cadence = Cadence.WEEKLY,
                deadlineMillis = now - 6 * hour, // Overdue
                estimatedMinutes = 35,
                personInCharge = "Darius Patel",
                reminderEnabled = true,
                reminderLeadHours = 24,
                lastCompletedMillis = now - 8 * day,
                updatedAtMillis = now
            )
        )
        val t4 = dao.insertTask(
            MaintenanceTaskEntity(
                machineId = m3,
                groupId = gUtilId,
                title = "Oil Separator & Intake Filter Inspection",
                description = "Inspect differential pressure gauge and clean pre-filter mesh.",
                cadence = Cadence.WEEKLY,
                deadlineMillis = now + 2 * day,
                estimatedMinutes = 45,
                personInCharge = "Elena Vance",
                reminderEnabled = true,
                reminderLeadHours = 24,
                lastCompletedMillis = now - 5 * day,
                updatedAtMillis = now
            )
        )
        val t5 = dao.insertTask(
            MaintenanceTaskEntity(
                machineId = m1,
                groupId = gCncId,
                title = "Spindle Taper Runout & Way-Lube Audit",
                description = "Measure spindle runout with test bar (<0.005mm) and purge lube lines.",
                cadence = Cadence.MONTHLY,
                deadlineMillis = now + 5 * day,
                estimatedMinutes = 90,
                personInCharge = "Sofia Novak",
                reminderEnabled = true,
                reminderLeadHours = 48,
                lastCompletedMillis = now - 25 * day,
                updatedAtMillis = now
            )
        )
        val t6 = dao.insertTask(
            MaintenanceTaskEntity(
                machineId = m2,
                groupId = gCncId,
                title = "Hydraulic Oil Viscosity & Return Filter Swap",
                description = "Sample ISO VG46 fluid and replace return line cartridge if >2.5 bar.",
                cadence = Cadence.MONTHLY,
                deadlineMillis = now - 1 * day, // Overdue
                estimatedMinutes = 75,
                personInCharge = "Marcus Chen",
                reminderEnabled = true,
                reminderLeadHours = 24,
                lastCompletedMillis = now - 32 * day,
                updatedAtMillis = now
            )
        )
        val t7 = dao.insertTask(
            MaintenanceTaskEntity(
                machineId = m5,
                groupId = gPackId,
                title = "Full Axis Gearbox Grease & Mastering Calibration",
                description = "Flush J1-J6 harmonic drive grease and verify zero-position mastering.",
                cadence = Cadence.YEARLY,
                deadlineMillis = now + 18 * day,
                estimatedMinutes = 240,
                personInCharge = "Sofia Novak",
                reminderEnabled = true,
                reminderLeadHours = 72,
                lastCompletedMillis = now - 347 * day,
                updatedAtMillis = now
            )
        )
        dao.insertTask(
            MaintenanceTaskEntity(
                machineId = m3,
                groupId = gSafetyId,
                title = "Pressure Vessel & Safety Relief Valve Certification",
                description = "Annual hydrostatic test & ASME relief valve bench certification.",
                cadence = Cadence.YEARLY,
                deadlineMillis = now + 40 * day,
                estimatedMinutes = 180,
                personInCharge = "Darius Patel",
                reminderEnabled = true,
                reminderLeadHours = 72,
                lastCompletedMillis = now - 325 * day,
                updatedAtMillis = now
            )
        )

        // 4. Engineer To-Do Reminders (Once, Daily, Weekly, Monthly, Yearly)
        dao.insertTodo(
            TodoReminderEntity(
                title = "Order 6x HF-40 Hydraulic Return Filters",
                notes = "Stock dipped to 1 unit after Bay 2 press service.",
                machineId = m2,
                groupId = gCncId,
                personInCharge = "Elena Vance",
                repeatRule = RepeatRule.ONCE,
                dueAtMillis = now + 4 * hour,
                reminderEnabled = true,
                priority = ReminderPriority.HIGH,
                updatedAtMillis = now
            )
        )
        dao.insertTodo(
            TodoReminderEntity(
                title = "Morning Shift Handover Safety Briefing",
                notes = "Review active Lockout-Tagout (LOTO) tags on Utility Room North.",
                machineId = m3,
                groupId = gSafetyId,
                personInCharge = "Elena Vance",
                repeatRule = RepeatRule.DAILY,
                dueAtMillis = now - 1 * hour,
                reminderEnabled = true,
                priority = ReminderPriority.URGENT,
                updatedAtMillis = now
            )
        )
        dao.insertTodo(
            TodoReminderEntity(
                title = "Export Weekly Plant Maintenance Excel Report",
                notes = "Send PDF & Excel summary to Plant Operations Lead.",
                groupId = gPackId,
                personInCharge = "Marcus Chen",
                repeatRule = RepeatRule.WEEKLY,
                dueAtMillis = now + 3 * day,
                reminderEnabled = true,
                priority = ReminderPriority.NORMAL,
                updatedAtMillis = now
            )
        )
        dao.insertTodo(
            TodoReminderEntity(
                title = "Monthly Critical Spare Parts Bin Count",
                notes = "Verify thermocouple, solenoid, and PTFE tape min-max levels.",
                personInCharge = "Darius Patel",
                repeatRule = RepeatRule.MONTHLY,
                dueAtMillis = now + 9 * day,
                reminderEnabled = true,
                priority = ReminderPriority.NORMAL,
                updatedAtMillis = now
            )
        )
        dao.insertTodo(
            TodoReminderEntity(
                title = "Annual ISO-9001 Calibration Audit Binder Prep",
                notes = "Archive yearly torque wrench and pressure gauge certificates.",
                groupId = gSafetyId,
                personInCharge = "Sofia Novak",
                repeatRule = RepeatRule.YEARLY,
                dueAtMillis = now + 25 * day,
                reminderEnabled = true,
                priority = ReminderPriority.HIGH,
                updatedAtMillis = now
            )
        )

        // 5. Historical Service & Malfunction Logs with Spare Parts Used
        dao.insertLogWithParts(
            ServiceLogEntity(
                machineId = m3,
                taskId = null,
                entryType = ServiceEntryType.MALFUNCTION_REPAIR,
                cadenceSnapshot = null,
                summaryTitle = "Thermostatic Bypass Valve Stuck Closed",
                malfunctionDetails = "Discharge temperature hit 101C triggering thermal trip. Disassembled cooler bypass housing and replaced faulty thermal element.",
                severity = Severity.CRITICAL,
                personInCharge = "Elena Vance",
                serviceDurationMinutes = 115,
                completedAtMillis = now - 5 * hour,
                deadlineWasMet = true,
                updatedAtMillis = now
            ),
            listOf(
                SparePartUsedEntity(serviceLogId = 0, partName = "Thermostatic Valve Kit", partCode = "AC-2901-1095", partDescription = "65C oil cooler bypass cartridge & spring kit", fromWarehouse = true, quantity = 1, unit = "kit"),
                SparePartUsedEntity(serviceLogId = 0, partName = "Viton O-Ring Seal 42mm", partCode = "OR-VT-42", partDescription = "High-temp fluoroelastomer housing seal", fromWarehouse = true, quantity = 2, unit = "pcs")
            )
        )

        dao.insertLogWithParts(
            ServiceLogEntity(
                machineId = m4,
                taskId = t2,
                entryType = ServiceEntryType.MALFUNCTION_REPAIR,
                cadenceSnapshot = Cadence.DAILY,
                summaryTitle = "Upper Sealing Jaw Burn-Through",
                malfunctionDetails = "Film edge wrinkling caused by torn PTFE strip and drifting K-type thermocouple.",
                severity = Severity.HIGH,
                personInCharge = "Marcus Chen",
                serviceDurationMinutes = 55,
                completedAtMillis = now - 2 * day,
                deadlineWasMet = true,
                updatedAtMillis = now
            ),
            listOf(
                SparePartUsedEntity(serviceLogId = 0, partName = "PTFE Non-Stick Jaw Tape", partCode = "MV-PTFE-38", partDescription = "38mm silicone-adhesive heat bar tape", fromWarehouse = true, quantity = 2, unit = "meters"),
                SparePartUsedEntity(serviceLogId = 0, partName = "K-Type Thermocouple Probe", partCode = "TC-K-120", partDescription = "M6 threaded 120mm stainless sensor probe", fromWarehouse = true, quantity = 1, unit = "pcs")
            )
        )

        dao.insertLogWithParts(
            ServiceLogEntity(
                machineId = m1,
                taskId = t1,
                entryType = ServiceEntryType.PREVENTIVE_SERVICE,
                cadenceSnapshot = Cadence.DAILY,
                summaryTitle = "Coolant Concentration & Skimmer Service",
                malfunctionDetails = "Adjusted Brix to 8.2% and cleared tramp oil skimmer belt.",
                severity = Severity.LOW,
                personInCharge = "Elena Vance",
                serviceDurationMinutes = 20,
                completedAtMillis = now - 1 * day,
                deadlineWasMet = true,
                updatedAtMillis = now
            ),
            listOf(
                SparePartUsedEntity(serviceLogId = 0, partName = "Semi-Synthetic Coolant Concentrate", partCode = "CL-SS-20L", partDescription = "Aluminum-safe CNC emulsion concentrate", fromWarehouse = true, quantity = 4, unit = "liters")
            )
        )

        dao.insertLogWithParts(
            ServiceLogEntity(
                machineId = m2,
                taskId = t6,
                entryType = ServiceEntryType.PREVENTIVE_SERVICE,
                cadenceSnapshot = Cadence.MONTHLY,
                summaryTitle = "Hydraulic Return Filter & Manifold Flush",
                malfunctionDetails = "Replaced clogged return cartridge (2.8 bar delta) and inspected proportional valve coils.",
                severity = Severity.MEDIUM,
                personInCharge = "Marcus Chen",
                serviceDurationMinutes = 80,
                completedAtMillis = now - 6 * day,
                deadlineWasMet = false,
                updatedAtMillis = now
            ),
            listOf(
                SparePartUsedEntity(serviceLogId = 0, partName = "Hydraulic Return Filter", partCode = "HF-40-10UM", partDescription = "10-micron spin-on return tank filter element", fromWarehouse = true, quantity = 1, unit = "pcs"),
                SparePartUsedEntity(serviceLogId = 0, partName = "ISO VG46 Hydraulic Oil", partCode = "OIL-VG46", partDescription = "Anti-wear hydraulic fluid drum stock", fromWarehouse = true, quantity = 15, unit = "liters")
            )
        )

        dao.insertLogWithParts(
            ServiceLogEntity(
                machineId = m5,
                taskId = t7,
                entryType = ServiceEntryType.PREVENTIVE_SERVICE,
                cadenceSnapshot = Cadence.YEARLY,
                summaryTitle = "Annual J1-J6 Gearbox Grease Replacement",
                malfunctionDetails = "Completed full Molywhite RE No.00 grease exchange and verified payload repeatability.",
                severity = Severity.LOW,
                personInCharge = "Sofia Novak",
                serviceDurationMinutes = 230,
                completedAtMillis = now - 14 * day,
                deadlineWasMet = true,
                updatedAtMillis = now
            ),
            listOf(
                SparePartUsedEntity(serviceLogId = 0, partName = "Molywhite RE No.00 Grease", partCode = "FN-GR-16KG", partDescription = "Harmonic drive robot reducer cartridge", fromWarehouse = true, quantity = 3, unit = "tubes")
            )
        )

        dao.insertLogWithParts(
            ServiceLogEntity(
                machineId = m1,
                taskId = t5,
                entryType = ServiceEntryType.PREVENTIVE_SERVICE,
                cadenceSnapshot = Cadence.MONTHLY,
                summaryTitle = "Spindle Runout & Way-Lube Pump Check",
                malfunctionDetails = "Runout measured 0.003mm. Replaced pneumatic way-lube inline check valve.",
                severity = Severity.LOW,
                personInCharge = "Darius Patel",
                serviceDurationMinutes = 85,
                completedAtMillis = now - 21 * day,
                deadlineWasMet = true,
                updatedAtMillis = now
            ),
            listOf(
                SparePartUsedEntity(serviceLogId = 0, partName = "Way-Lube Check Valve 6mm", partCode = "WL-CV-06", partDescription = "Brass 6mm compression non-return valve", fromWarehouse = true, quantity = 1, unit = "pcs"),
                SparePartUsedEntity(serviceLogId = 0, partName = "ISO VG68 Slideway Oil", partCode = "OIL-VG68", partDescription = "Tackified linear guideway lubricant", fromWarehouse = true, quantity = 2, unit = "liters")
            )
        )

        // 6. Warehouse Spare Parts Catalog (Codes & Descriptions)
        seedWarehouseCatalog(dao, now)
    }

    private suspend fun seedWarehouseCatalog(dao: MachinaDao, now: Long) {
        val catalog = listOf(
            WarehousePartEntity(partCode = "AC-2901-1095", partName = "Thermostatic Valve Kit", partDescription = "65C oil cooler bypass cartridge & spring kit for GA-55 compressor", defaultUnit = "kit", updatedAtMillis = now),
            WarehousePartEntity(partCode = "OR-VT-42", partName = "Viton O-Ring Seal 42mm", partDescription = "High-temp fluoroelastomer 42x3mm housing seal ring", defaultUnit = "pcs", updatedAtMillis = now),
            WarehousePartEntity(partCode = "MV-PTFE-38", partName = "PTFE Non-Stick Jaw Tape", partDescription = "38mm silicone-adhesive heat bar tape for thermoformer sealing jaw", defaultUnit = "meters", updatedAtMillis = now),
            WarehousePartEntity(partCode = "TC-K-120", partName = "K-Type Thermocouple Probe", partDescription = "M6 threaded 120mm stainless temperature sensor probe", defaultUnit = "pcs", updatedAtMillis = now),
            WarehousePartEntity(partCode = "CL-SS-20L", partName = "Semi-Synthetic Coolant", partDescription = "Aluminum-safe CNC cutting emulsion concentrate (20L pail ref)", defaultUnit = "liters", updatedAtMillis = now),
            WarehousePartEntity(partCode = "HF-40-10UM", partName = "Hydraulic Return Filter", partDescription = "10-micron spin-on hydraulic return tank filter element", defaultUnit = "pcs", updatedAtMillis = now),
            WarehousePartEntity(partCode = "OIL-VG46", partName = "ISO VG46 Hydraulic Oil", partDescription = "Anti-wear HLP hydraulic system fluid", defaultUnit = "liters", updatedAtMillis = now),
            WarehousePartEntity(partCode = "FN-GR-16KG", partName = "Molywhite RE No.00 Grease", partDescription = "Harmonic drive robot reducer cartridge 400g", defaultUnit = "tubes", updatedAtMillis = now),
            WarehousePartEntity(partCode = "WL-CV-06", partName = "Way-Lube Check Valve 6mm", partDescription = "Brass 6mm compression inline non-return valve", defaultUnit = "pcs", updatedAtMillis = now),
            WarehousePartEntity(partCode = "OIL-VG68", partName = "ISO VG68 Slideway Oil", partDescription = "Tackified linear guideway and ballscrew lubricant", defaultUnit = "liters", updatedAtMillis = now),
            WarehousePartEntity(partCode = "BRG-6205-2RS", partName = "Deep Groove Bearing 6205-2RS", partDescription = "SKF 25x52x15mm double-sealed conveyor & motor bearing", defaultUnit = "pcs", updatedAtMillis = now),
            WarehousePartEntity(partCode = "SOL-24DC-52", partName = "5/2 Pneumatic Solenoid Valve", partDescription = "24V DC NAMUR manifold directional control valve G1/4", defaultUnit = "pcs", updatedAtMillis = now),
            WarehousePartEntity(partCode = "RLY-24DC-4P", partName = "Safety Control Relay 24VDC", partDescription = "Dual-channel E-stop & light curtain safety relay module", defaultUnit = "pcs", updatedAtMillis = now),
            WarehousePartEntity(partCode = "SEN-IND-M12", partName = "M12 Inductive Proximity Sensor", partDescription = "PNP NO flush 4mm sensing distance M12 4-pin quick disconnect", defaultUnit = "pcs", updatedAtMillis = now),
            WarehousePartEntity(partCode = "BLT-HTD-8M", partName = "HTD-8M Timing Belt 1120", partDescription = "Fiberglass-corded neoprene drive belt 30mm width", defaultUnit = "pcs", updatedAtMillis = now),
            WarehousePartEntity(partCode = "FUS-CC-10A", partName = "Control Fuse 10A 600V", partDescription = "Class CC time-delay ceramic cartridge fuse 10x38mm", defaultUnit = "pcs", updatedAtMillis = now)
        )
        catalog.forEach { dao.insertWarehousePart(it) }
    }
}
