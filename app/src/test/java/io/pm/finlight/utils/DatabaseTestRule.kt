package io.pm.finlight.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pm.finlight.data.db.AppDatabase
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * A JUnit TestRule that creates an in-memory Room database before each test
 * and closes it afterward. This ensures each test runs in a clean, isolated
 * database environment.
 */
class DatabaseTestRule : TestWatcher() {

    lateinit var db: AppDatabase
        private set

    override fun starting(description: Description) {
        super.starting(description)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            // Allowing main thread queries is only advisable for testing.
            .allowMainThreadQueries()
            .build()
    }

    override fun finished(description: Description) {
        super.finished(description)
        db.close()
    }
}