package de.run.app.data.fit

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.garmin.fit.DateTime
import com.garmin.fit.File
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import de.run.app.data.local.RunDatabase
import de.run.app.domain.repository.FitActivityImportResult
import java.io.File as JavaFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidFitActivityImportRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun repeatedFitImportKeepsOneWorkoutAndReportsTheSecondAsAlreadyPresent() {
        runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, RunDatabase::class.java).build()
        val fitFile = JavaFile.createTempFile("lap-fit-import", ".fit", context.cacheDir)
        writeActivityFit(fitFile)
        val importer = AndroidFitActivityImportRepository(context.contentResolver, database, context.filesDir, context.cacheDir)

        val first = importer.importFile(Uri.fromFile(fitFile).toString()) as FitActivityImportResult.Imported
        val second = importer.importFile(Uri.fromFile(fitFile).toString()) as FitActivityImportResult.Imported
        val saved = database.workoutDao().observeRecent().first()

        assertEquals(1, first.newlyImportedActivities)
        assertEquals(1, second.alreadyPresentActivities)
        assertEquals(0, second.newlyImportedActivities)
        assertEquals(1, saved.size)
        database.close()
        fitFile.delete()
        }
    }

    private fun writeActivityFit(file: JavaFile) {
        val encoder = FileEncoder(file)
        encoder.write(FileIdMesg().apply {
            type = File.ACTIVITY
            manufacturer = 1
            timeCreated = DateTime(1_700_000_000L)
        })
        encoder.write(SessionMesg().apply {
            startTime = DateTime(1_700_000_000L)
            timestamp = DateTime(1_700_003_600L)
            sport = Sport.RUNNING
            totalElapsedTime = 3_600f
            totalDistance = 5_000f
        })
        encoder.close()
    }
}
