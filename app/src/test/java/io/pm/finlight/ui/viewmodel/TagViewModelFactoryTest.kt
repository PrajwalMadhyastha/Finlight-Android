package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.ITagRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.repository.ITripRepository
import io.pm.finlight.di.ServiceLocator
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TagViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: TagViewModelFactory
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase.setTestInstance(db)
        factory = TagViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
    }

    @Test
    fun create_withTagViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockTagRepo: ITagRepository = mockk(relaxed = true)
        val mockTripRepo: ITripRepository = mockk(relaxed = true)

        ServiceLocator.setTagRepository(mockTagRepo)
        ServiceLocator.setTripRepository(mockTripRepo)

        val viewModel = factory.create(TagViewModel::class.java)

        assertNotNull(viewModel)

        val tagField =
            TagViewModel::class.java.getDeclaredField("tagRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTagRepo, tagField)

        val tripField =
            TagViewModel::class.java.getDeclaredField("tripRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTripRepo, tripField)
    }

    @Test
    fun create_withExtras_returnsTagViewModel() {
        val viewModel = factory.create(TagViewModel::class.java, CreationExtras.Empty)
        assertNotNull(viewModel)
    }

    @Test
    fun create_withOverriddenViewModel_returnsMockedInstance() {
        val mockViewModel: TagViewModel = mockk(relaxed = true)
        ServiceLocator.setTagViewModel(mockViewModel)

        val resolved = factory.create(TagViewModel::class.java)
        assertSame(mockViewModel, resolved)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}

