package com.farhan.today.data.db

import androidx.room.withTransaction
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Data contoh pertama kali aplikasi dibuka, relatif terhadap "hari ini"
 * supaya Today selalu terlihat seperti desain FigCraft.
 */
class SeedData(private val db: TodayDatabase, private val clock: Clock) {

    suspend fun seed() = db.withTransaction {
        val now = clock.millis()
        val today = LocalDate.now(clock)
        val zone = clock.zone
        fun at(date: LocalDate, h: Int, m: Int = 0) = ZonedDateTime.of(date, LocalTime.of(h, m), zone).toInstant().toEpochMilli()

        // ---------------- Latihan
        val w = db.workoutDao()
        val catalog = exerciseCatalog.map { (name, group, eq) ->
            ExerciseEntity(name = name, muscleGroup = group, equipment = eq, createdAt = now, updatedAt = now)
        }
        w.upsertExercises(catalog)
        fun ex(name: String) = catalog.first { it.name == name }.id

        val push = RoutineEntity(name = "Push Day", focus = "Dada, bahu, dan trisep", createdAt = now, updatedAt = now)
        val pull = RoutineEntity(name = "Pull Day", focus = "Punggung dan bisep", createdAt = now, updatedAt = now)
        val leg = RoutineEntity(name = "Leg Day", focus = "Kaki dan core", createdAt = now, updatedAt = now)
        listOf(push, pull, leg).forEach { w.upsertRoutine(it) }

        fun plan(routine: RoutineEntity, vararg names: String) = names.mapIndexed { i, n ->
            RoutineExerciseEntity(
                routineId = routine.id, exerciseId = ex(n), position = i, targetSets = 3, targetReps = 8,
                targetWeightKg = null, restSeconds = 90, createdAt = now, updatedAt = now,
            )
        }
        w.upsertRoutineExercises(
            plan(push, "Bench press", "Overhead press", "Incline dumbbell press", "Tricep pushdown") +
                plan(pull, "Deadlift", "Pull-up", "Barbell row", "Lat pulldown", "Bicep curl") +
                plan(leg, "Back squat", "Romanian deadlift", "Leg press", "Plank")
        )

        // Jadwal mingguan: hari ini selalu Push Day agar kartu "Berikutnya" terisi.
        val todayDow = today.dayOfWeek
        val schedule = listOf(
            push to todayDow,
            push to todayDow.plus(3),
            pull to todayDow.plus(1),
            leg to todayDow.plus(5),
        )
        w.upsertSchedules(schedule.map { (r, dow) ->
            RoutineScheduleEntity(routineId = r.id, dayOfWeek = dow.value, time = LocalTime.of(17, 0), createdAt = now, updatedAt = now)
        })

        w.upsertSession(
            WorkoutSessionEntity(
                routineId = push.id, name = push.name, plannedDate = today, plannedTime = LocalTime.of(17, 0),
                status = SessionStatus.PLANNED, createdAt = now, updatedAt = now,
            )
        )
        listOf(pull to 3L to 42, leg to 5L to 37).forEach { (pair, minutes) ->
            val (r, daysAgo) = pair
            val date = today.minusDays(daysAgo)
            val start = at(date, 17)
            val session = WorkoutSessionEntity(
                routineId = r.id, name = r.name, plannedDate = date, plannedTime = LocalTime.of(17, 0),
                startedAt = start, endedAt = start + minutes * 60_000L, status = SessionStatus.DONE,
                createdAt = now, updatedAt = now,
            )
            w.upsertSession(session)
        }

        // ---------------- Karier
        val c = db.careerDao()
        val gdp = JobApplicationEntity(
            company = "GDP Labs", role = "Software Development Intern", location = "Jakarta",
            source = "LinkedIn", status = ApplicationStatus.CODING_TEST, appliedDate = today.minusDays(9),
            notes = "Siapkan kamera dan latihan algoritma.", createdAt = now, updatedAt = now,
        )
        val studio = JobApplicationEntity(
            company = "Studio Kode", role = "Backend Engineer Intern", source = "Glints",
            status = ApplicationStatus.APPLIED, appliedDate = today.minusDays(4), createdAt = now, updatedAt = now,
        )
        val ruang = JobApplicationEntity(
            company = "Ruang Digital", role = "Android Developer Intern", status = ApplicationStatus.SAVED,
            deadlineDate = today.plusDays(6), createdAt = now, updatedAt = now,
        )
        listOf(gdp, studio, ruang).forEach { c.upsertApplication(it) }
        listOf(
            gdp to listOf(null to ApplicationStatus.SAVED, ApplicationStatus.SAVED to ApplicationStatus.APPLIED, ApplicationStatus.APPLIED to ApplicationStatus.CODING_TEST),
            studio to listOf(null to ApplicationStatus.APPLIED),
            ruang to listOf(null to ApplicationStatus.SAVED),
        ).forEach { (app, changes) ->
            changes.forEachIndexed { i, (from, to) ->
                c.upsertStatusChange(
                    ApplicationStatusChangeEntity(
                        applicationId = app.id, fromStatus = from, toStatus = to,
                        changedAt = now - (changes.size - i) * 86_400_000L, createdAt = now, updatedAt = now,
                    )
                )
            }
        }
        c.upsertEvent(
            JobEventEntity(
                applicationId = gdp.id, type = JobEventType.CODING_TEST, title = "Coding test",
                startAt = at(today, 20), durationMinutes = 90, mode = EventMode.ONLINE,
                reminderMinutesBefore = 30, createdAt = now, updatedAt = now,
            )
        )
        c.upsertEvent(
            JobEventEntity(
                applicationId = gdp.id, type = JobEventType.INTERVIEW, title = "Interview GDP Labs",
                startAt = at(today.plusDays(1), 10), durationMinutes = 60, mode = EventMode.ONLINE,
                reminderMinutesBefore = 30, createdAt = now, updatedAt = now,
            )
        )

        // ---------------- Keuangan
        val f = db.financeDao()
        val categories = expenseCategories.mapIndexed { i, (name, icon) ->
            CategoryEntity(name = name, kind = MoneyKind.EXPENSE, iconKey = icon, colorKey = "finance", position = i, createdAt = now, updatedAt = now)
        } + incomeCategories.mapIndexed { i, (name, icon) ->
            CategoryEntity(name = name, kind = MoneyKind.INCOME, iconKey = icon, colorKey = "income", position = i, createdAt = now, updatedAt = now)
        }
        f.upsertCategories(categories)
        fun cat(name: String) = categories.first { it.name == name }.id

        f.upsertBudget(
            BudgetEntity(name = "Uang makan", categoryId = cat("Makan"), amount = 100_000, period = BudgetPeriod.WEEKLY, createdAt = now, updatedAt = now)
        )
        // Total makan minggu ini Rp58.000 → sisa Rp42.000, seperti desain.
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        f.upsertTransactions(
            listOf(
                MoneyTransactionEntity(kind = MoneyKind.EXPENSE, amount = 34_000, categoryId = cat("Makan"), note = "Makan malam", occurredAt = minOf(at(weekStart, 7), now), createdAt = now, updatedAt = now),
                MoneyTransactionEntity(kind = MoneyKind.EXPENSE, amount = 24_000, categoryId = cat("Makan"), note = "Makan siang", occurredAt = minOf(at(today, 12), now), createdAt = now, updatedAt = now),
                MoneyTransactionEntity(kind = MoneyKind.EXPENSE, amount = 8_000, categoryId = cat("Transport"), note = "KRL", occurredAt = minOf(at(today, 8), now), createdAt = now, updatedAt = now),
            )
        )
        f.upsertBill(
            BillEntity(name = "Membership gym", amount = 150_000, categoryId = cat("Kesehatan"), repeat = BillRepeat.MONTHLY, nextDueDate = today.plusDays(3), reminderDaysBefore = 1, createdAt = now, updatedAt = now)
        )

        // ---------------- Tugas & Proyek
        val t = db.taskDao()
        val skripsi = ProjectEntity(name = "Skripsi", description = "Tugas akhir S1", createdAt = now, updatedAt = now)
        t.upsertProject(skripsi)
        t.upsertTasks(
            listOf(
                TaskEntity(projectId = skripsi.id, title = "Revisi Bab 3", note = "Perbarui metode penelitian", dueDate = today.plusDays(1), priority = TaskPriority.HIGH, position = 0, createdAt = now, updatedAt = now),
                TaskEntity(projectId = skripsi.id, title = "Olah data", note = "Bersihkan data sebelum analisis", dueDate = today.plusDays(4), position = 1, createdAt = now, updatedAt = now),
                TaskEntity(projectId = skripsi.id, title = "Bimbingan Bab 4", dueDate = today.plusDays(10), position = 2, createdAt = now, updatedAt = now),
                TaskEntity(projectId = skripsi.id, title = "Kumpulkan referensi", status = TaskStatus.DONE, completedAt = now - 5 * 86_400_000L, dueDate = today.minusDays(5), position = 3, createdAt = now, updatedAt = now),
            )
        )
    }

    companion object {
        val exerciseCatalog = listOf(
            Triple("Bench press", MuscleGroup.CHEST, Equipment.BARBELL),
            Triple("Incline dumbbell press", MuscleGroup.CHEST, Equipment.DUMBBELL),
            Triple("Push-up", MuscleGroup.CHEST, Equipment.BODYWEIGHT),
            Triple("Chest fly", MuscleGroup.CHEST, Equipment.CABLE),
            Triple("Deadlift", MuscleGroup.BACK, Equipment.BARBELL),
            Triple("Pull-up", MuscleGroup.BACK, Equipment.BODYWEIGHT),
            Triple("Barbell row", MuscleGroup.BACK, Equipment.BARBELL),
            Triple("Lat pulldown", MuscleGroup.BACK, Equipment.MACHINE),
            Triple("Seated cable row", MuscleGroup.BACK, Equipment.CABLE),
            Triple("Back squat", MuscleGroup.LEGS, Equipment.BARBELL),
            Triple("Romanian deadlift", MuscleGroup.LEGS, Equipment.BARBELL),
            Triple("Leg press", MuscleGroup.LEGS, Equipment.MACHINE),
            Triple("Walking lunge", MuscleGroup.LEGS, Equipment.DUMBBELL),
            Triple("Calf raise", MuscleGroup.LEGS, Equipment.MACHINE),
            Triple("Overhead press", MuscleGroup.SHOULDERS, Equipment.BARBELL),
            Triple("Lateral raise", MuscleGroup.SHOULDERS, Equipment.DUMBBELL),
            Triple("Face pull", MuscleGroup.SHOULDERS, Equipment.CABLE),
            Triple("Bicep curl", MuscleGroup.ARMS, Equipment.DUMBBELL),
            Triple("Hammer curl", MuscleGroup.ARMS, Equipment.DUMBBELL),
            Triple("Tricep pushdown", MuscleGroup.ARMS, Equipment.CABLE),
            Triple("Dips", MuscleGroup.ARMS, Equipment.BODYWEIGHT),
            Triple("Plank", MuscleGroup.CORE, Equipment.BODYWEIGHT),
            Triple("Hanging leg raise", MuscleGroup.CORE, Equipment.BODYWEIGHT),
            Triple("Russian twist", MuscleGroup.CORE, Equipment.BODYWEIGHT),
            Triple("Burpee", MuscleGroup.FULL_BODY, Equipment.BODYWEIGHT),
        )
        val expenseCategories = listOf(
            "Makan" to "food", "Transport" to "train", "Kos" to "home", "Kuliah" to "school",
            "Hiburan" to "fun", "Kesehatan" to "health", "Lainnya" to "other",
        )
        val incomeCategories = listOf("Uang saku" to "wallet", "Gaji/Freelance" to "trend")
    }
}
