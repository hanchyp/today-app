package com.farhan.today.data.db

import android.content.res.AssetManager
import org.json.JSONArray
import java.util.UUID

/** [animation]: path WebP animasi relatif ke `assets/exercise-media/` (frame pertama = gambar diam). */
internal data class CatalogExercise(val exercise: ExerciseEntity, val equipment: String, val steps: List<String>, val animation: String)

private fun catalogId(sourceId: String): String =
    UUID.nameUUIDFromBytes("hasaneyldrm/exercises-dataset:$sourceId".toByteArray()).toString()

// Existing sample routines reference the original exercise IDs. Resolve media
// without replacing those IDs or changing the user's workout history.
private val sampleAnimations = mapOf(
    "Bench press" to "0025",
    "Incline dumbbell press" to "0314",
    "Push-up" to "0662",
    "Chest fly" to "0227",
    "Deadlift" to "0032",
    "Pull-up" to "0652",
    "Barbell row" to "0027",
    "Lat pulldown" to "2330",
    "Seated cable row" to "0861",
    "Back squat" to "1436",
    "Romanian deadlift" to "0085",
    "Leg press" to "0739",
    "Calf raise" to "0605",
    "Overhead press" to "1456",
    "Lateral raise" to "0334",
    "Bicep curl" to "0294",
    "Hammer curl" to "0313",
    "Tricep pushdown" to "0201",
    "Dips" to "0814",
    "Hanging leg raise" to "0472",
    "Russian twist" to "0687",
    "Burpee" to "1160",
).mapValues { (_, sourceId) -> catalogId(sourceId) }

internal fun Map<String, CatalogExercise>.animationFor(exercise: ExerciseEntity): String? {
    this[exercise.id]?.let { return it.animation }
    if (Triple(exercise.name, exercise.muscleGroup, exercise.equipment) !in SeedData.exerciseCatalog) return null
    return sampleAnimations[exercise.name]?.let { this[it]?.animation }
}

internal fun loadExerciseCatalog(assets: AssetManager, now: Long): List<CatalogExercise> {
    val rows = JSONArray(assets.open("exercise-catalog.json").bufferedReader().use { it.readText() })
    return List(rows.length()) { index ->
        val row = rows.getJSONObject(index)
        val group = when (row.getString("body_part")) {
            "chest" -> MuscleGroup.CHEST
            "back" -> MuscleGroup.BACK
            "upper legs", "lower legs" -> MuscleGroup.LEGS
            "shoulders" -> MuscleGroup.SHOULDERS
            "upper arms", "lower arms" -> MuscleGroup.ARMS
            "waist" -> MuscleGroup.CORE
            else -> MuscleGroup.FULL_BODY
        }
        val equipment = when (val source = row.getString("equipment")) {
            "barbell", "ez barbell", "olympic barbell", "trap bar" -> Equipment.BARBELL
            "dumbbell" -> Equipment.DUMBBELL
            "body weight" -> Equipment.BODYWEIGHT
            "cable" -> Equipment.CABLE
            "stationary bike", "upper body ergometer" -> Equipment.MACHINE
            else -> if (source.endsWith(" machine")) Equipment.MACHINE else Equipment.OTHER
        }
        val sourceId = row.getString("id")
        val exercise = ExerciseEntity(
            id = catalogId(sourceId),
            name = row.getString("name").replaceFirstChar { it.uppercaseChar() },
            muscleGroup = group,
            equipment = equipment,
            createdAt = now,
            updatedAt = now,
        )
        val steps = row.getJSONArray("instructions")
        CatalogExercise(exercise, row.getString("equipment"), List(steps.length()) { steps.getString(it) }, row.getString("animation"))
    }
}
