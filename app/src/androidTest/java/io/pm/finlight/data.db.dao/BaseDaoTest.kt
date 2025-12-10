package io.pm.finlight.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.data.db.AppDatabase
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Abstract base class for all DAO instrumented tests.
 * Handles the creation and closing of an in-memory database.
 */
@RunWith(AndroidJUnit4::class)
abstract class BaseDaoTest {

    protected lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Using an in-memory database because the information stored here disappears when the
        // process is killed.
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries() // Allowed for testing to keep tests synchronous
            .build()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }
}