package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.TransactionAnalyticsDao
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.di.assertThrowsUnknownViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AnalysisDetailViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: AnalysisDetailViewModelFactory
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase.setTestInstance(db)
        factory =
            AnalysisDetailViewModelFactory(
                application = application,
                dimension = AnalysisDimension.CATEGORY,
                dimensionId = "1",
                startDate = 100L,
                endDate = 200L,
            )
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
    }

    @Test
    fun create_withAnalysisDetailViewModelClass_resolvesFromServiceLocator() {
        val vm = factory.create(AnalysisDetailViewModel::class.java)
        assertNotNull(vm)

        val dimField =
            AnalysisDetailViewModel::class.java.getDeclaredField("dimension").apply {
                isAccessible = true
            }.get(vm)
        assertEquals(AnalysisDimension.CATEGORY, dimField)

        val idField =
            AnalysisDetailViewModel::class.java.getDeclaredField("dimensionId").apply {
                isAccessible = true
            }.get(vm)
        assertEquals("1", idField)

        val startField =
            AnalysisDetailViewModel::class.java.getDeclaredField("startDate").apply {
                isAccessible = true
            }.get(vm)
        assertEquals(100L, startField)

        val endField =
            AnalysisDetailViewModel::class.java.getDeclaredField("endDate").apply {
                isAccessible = true
            }.get(vm)
        assertEquals(200L, endField)

        val daoField =
            AnalysisDetailViewModel::class.java.getDeclaredField("transactionAnalyticsDao").apply {
                isAccessible = true
            }.get(vm)
        assertNotNull(daoField)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        assertThrowsUnknownViewModel(factory)
    }

    @Test
    fun create_withExtras_returnsAnalysisDetailViewModel() {
        val vm = factory.create(AnalysisDetailViewModel::class.java, CreationExtras.Empty)
        assertNotNull(vm)
    }

    @Test
    fun create_withOverriddenViewModel_returnsMockedInstance() {
        val mockVM: AnalysisDetailViewModel = mockk(relaxed = true)
        ServiceLocator.setAnalysisDetailViewModel(mockVM)

        val resolved = factory.create(AnalysisDetailViewModel::class.java)
        assertSame(mockVM, resolved)
    }

    @Test
    fun create_withExplicitOverrides_usesPassedDependencies() {
        val mockDao: TransactionAnalyticsDao = mockk(relaxed = true)
        val customFactory =
            AnalysisDetailViewModelFactory(
                application = application,
                dimension = AnalysisDimension.TAG,
                dimensionId = "2",
                startDate = 0L,
                endDate = 100L,
                transactionAnalyticsDao = mockDao,
            )

        val vm = customFactory.create(AnalysisDetailViewModel::class.java)
        assertNotNull(vm)

        val daoField =
            AnalysisDetailViewModel::class.java.getDeclaredField("transactionAnalyticsDao").apply {
                isAccessible = true
            }.get(vm)
        assertSame(mockDao, daoField)

        val dimField =
            AnalysisDetailViewModel::class.java.getDeclaredField("dimension").apply {
                isAccessible = true
            }.get(vm)
        assertEquals(AnalysisDimension.TAG, dimField)

        val idField =
            AnalysisDetailViewModel::class.java.getDeclaredField("dimensionId").apply {
                isAccessible = true
            }.get(vm)
        assertEquals("2", idField)

        val startField =
            AnalysisDetailViewModel::class.java.getDeclaredField("startDate").apply {
                isAccessible = true
            }.get(vm)
        assertEquals(0L, startField)

        val endField =
            AnalysisDetailViewModel::class.java.getDeclaredField("endDate").apply {
                isAccessible = true
            }.get(vm)
        assertEquals(100L, endField)
    }
}
