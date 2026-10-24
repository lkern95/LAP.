package de.run.app.data.fit

import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutActivityType
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

internal class GarminTextActivityDecoder {
    fun decode(extension: String, bytes: ByteArray): List<Workout>? {
        if (extension !in SUPPORTED_EXTENSIONS || bytes.size > MAX_FILE_BYTES) return null
        return runCatching {
            val handler = ActivityHandler(extension)
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature(DISALLOW_DOCTYPE, true)
                setFeature(EXTERNAL_GENERAL_ENTITIES, false)
                setFeature(EXTERNAL_PARAMETER_ENTITIES, false)
            }
            factory.newSAXParser().xmlReader.apply {
                entityResolver = org.xml.sax.EntityResolver { _, _ -> org.xml.sax.InputSource(ByteArrayInputStream(ByteArray(0))) }
                contentHandler = handler
                parse(org.xml.sax.InputSource(ByteArrayInputStream(bytes)))
            }
            val sha = sha256(bytes)
            handler.workouts().mapIndexed { index, workout ->
                workout.copy(sourceId = "$extension:$sha:$index")
            }
        }.getOrNull()
    }

    private class ActivityHandler(private val extension: String) : DefaultHandler() {
        private val text = StringBuilder()
        private var type = WorkoutActivityType.OTHER
        private var latitude: Double? = null
        private var longitude: Double? = null
        private var currentPointTime: Long? = null
        private var currentDistance: Double? = null
        private var currentHeartRate: Int? = null
        private var currentCadence: Int? = null
        private var previousPoint: Pair<Double, Double>? = null
        private var startTimeMillis: Long? = null
        private var endTimeMillis: Long? = null
        private var declaredDurationSeconds: Double? = null
        private var totalDistanceMeters = 0.0
        private val distances = mutableListOf<Double>()
        private val heartRates = mutableListOf<Int>()
        private val cadences = mutableListOf<Int>()
        private val workouts = mutableListOf<Workout>()
        private var activityIndex = 0
        private var capture: String? = null

        override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
            text.setLength(0)
            val name = localName.orEmpty().ifBlank { qName.orEmpty().substringAfter(':') }
            capture = name.takeIf { it in CAPTURE_TAGS }
            when (name.lowercase(Locale.ROOT)) {
                "activity" -> type = typeFrom(attrs?.getValue("Sport"))
                "trkpt" -> {
                    latitude = attrs?.getValue("lat")?.toDoubleOrNull()
                    longitude = attrs?.getValue("lon")?.toDoubleOrNull()
                    currentPointTime = null
                    currentHeartRate = null
                    currentCadence = null
                }
                "trackpoint" -> {
                    currentPointTime = null
                    currentDistance = null
                    currentHeartRate = null
                    currentCadence = null
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (capture != null && text.length < MAX_TEXT_LENGTH) text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = localName.orEmpty().ifBlank { qName.orEmpty().substringAfter(':') }
            val value = text.toString().trim()
            when (name.lowercase(Locale.ROOT)) {
                "time" -> parseTime(value)?.let { currentPointTime = it }
                "id" -> parseTime(value)?.let { if (startTimeMillis == null) startTimeMillis = it }
                "totaltimeseconds" -> value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }?.let { declaredDurationSeconds = it }
                "type" -> type = typeFrom(value)
                "distancemeters" -> value.toDoubleOrNull()?.takeIf(Double::isFinite)?.let { currentDistance = it }
                "value" -> value.toIntOrNull()?.takeIf { it in 1..255 }?.let { currentHeartRate = it }
                "hr" -> value.toIntOrNull()?.takeIf { it in 1..255 }?.let { currentHeartRate = it }
                "cad", "cadence" -> value.toIntOrNull()?.takeIf { it in 1..255 }?.let { currentCadence = it }
                "trkpt" -> addGpxPoint()
                "trackpoint" -> addTcxPoint()
                "activity" -> finishActivity()
            }
            capture = null
        }

        fun workouts(): List<Workout> {
            if (extension == "gpx") finishActivity()
            return workouts
        }

        private fun addGpxPoint() {
            val lat = latitude ?: return
            val lon = longitude ?: return
            val point = lat to lon
            previousPoint?.let { totalDistanceMeters += haversine(it, point) }
            previousPoint = point
            currentPointTime?.let(::recordTime)
            currentHeartRate?.let(heartRates::add)
            currentCadence?.let(cadences::add)
        }

        private fun addTcxPoint() {
            currentPointTime?.let(::recordTime)
            currentDistance?.let(distances::add)
            currentHeartRate?.let(heartRates::add)
            currentCadence?.let(cadences::add)
        }

        private fun finishActivity() {
            val start = startTimeMillis ?: return
            val end = endTimeMillis ?: declaredDurationSeconds?.let { start + (it * 1_000.0).toLong() } ?: return
            if (end <= start) return
            val distance = when {
                distances.isNotEmpty() -> distances.maxOrNull()
                totalDistanceMeters > 0.0 -> totalDistanceMeters
                else -> null
            }
            workouts += Workout(
                sourceId = "$extension:pending:${activityIndex++}",
                sourceName = "Garmin-Backup",
                title = type.label,
                startedAtMillis = start,
                endedAtMillis = end,
                distanceMeters = distance,
                averageHeartRateBpm = heartRates.averageIntOrNull(),
                maximumHeartRateBpm = heartRates.maxOrNull(),
                activityType = type,
                averageCadenceRpm = cadences.averageIntOrNull()
            )
            resetActivity()
        }

        private fun resetActivity() {
            type = WorkoutActivityType.OTHER
            currentPointTime = null
            startTimeMillis = null
            endTimeMillis = null
            declaredDurationSeconds = null
            previousPoint = null
            totalDistanceMeters = 0.0
            distances.clear()
            heartRates.clear()
            cadences.clear()
        }

        private fun typeFrom(value: String?): WorkoutActivityType = when (value?.lowercase(Locale.ROOT)) {
            "running", "run", "trailrunning" -> WorkoutActivityType.RUNNING
            "walking", "walk" -> WorkoutActivityType.WALKING
            "biking", "cycling", "bicycle" -> WorkoutActivityType.CYCLING
            "strength", "training", "weighttraining" -> WorkoutActivityType.STRENGTH
            else -> WorkoutActivityType.OTHER
        }

        private fun parseTime(value: String): Long? = runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()

        private fun recordTime(value: Long) {
            if (startTimeMillis == null || value < startTimeMillis!!) startTimeMillis = value
            if (endTimeMillis == null || value > endTimeMillis!!) endTimeMillis = value
        }

        private fun haversine(first: Pair<Double, Double>, second: Pair<Double, Double>): Double {
            val lat1 = Math.toRadians(first.first)
            val lat2 = Math.toRadians(second.first)
            val deltaLat = lat2 - lat1
            val deltaLon = Math.toRadians(second.second - first.second)
            val a = kotlin.math.sin(deltaLat / 2).let { it * it } +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * kotlin.math.sin(deltaLon / 2).let { it * it }
            return 2 * EARTH_RADIUS_METERS * kotlin.math.asin(kotlin.math.sqrt(a.coerceIn(0.0, 1.0)))
        }

        private companion object {
            const val MAX_TEXT_LENGTH = 128
            const val EARTH_RADIUS_METERS = 6_371_000.0
            val CAPTURE_TAGS = setOf("time", "Id", "TotalTimeSeconds", "type", "DistanceMeters", "Value", "hr", "cad", "cadence")
        }
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("gpx", "tcx")
        const val MAX_FILE_BYTES = 64 * 1024 * 1024
        const val DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"
        const val EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities"
        const val EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities"
    }
}

private fun List<Int>.averageIntOrNull(): Int? = average().takeIf(Double::isFinite)?.toInt()

private fun sha256(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }
