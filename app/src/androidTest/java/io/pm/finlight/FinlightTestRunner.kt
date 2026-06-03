package io.pm.finlight

import android.app.Application
import androidx.room.Room
import androidx.test.runner.AndroidJUnitRunner
import io.pm.finlight.data.db.AppDatabase

class FinlightTestRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application?) {
        super.callApplicationOnCreate(app)
        app?.let { application ->
            // Create an in-memory database for UI tests, bypassing SQLCipher and disk IO
            val testDatabase =
                Room.inMemoryDatabaseBuilder(
                    application.applicationContext,
                    AppDatabase::class.java
                )
                    // Allow main thread queries in tests to prevent deadlocks when UI components
                    // access the DB synchronously during render.
                    .allowMainThreadQueries()
                    .build()

            // Inject the in-memory database into the AppDatabase singleton
            AppDatabase.setTestInstance(testDatabase)
        }
    }
}
