package io.pm.finlight.utils

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.MainActivity
import io.pm.finlight.TestApplication
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ContextExtensionsTest {
    @Test
    fun findActivity_returnsActivityWhenContextIsComponentActivity() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).get()
        assertSame(activity, activity.findActivity())
    }

    @Test
    fun findActivity_returnsActivityWhenWrappedInContextWrapper() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).get()
        val wrapper1 = ContextWrapper(activity)
        val wrapper2 = ContextWrapper(wrapper1)

        assertSame(activity, wrapper1.findActivity())
        assertSame(activity, wrapper2.findActivity())
    }

    @Test
    fun findActivity_returnsNullWhenBaseContextIsNotActivity() {
        val nonActivityContext: Context = ApplicationProvider.getApplicationContext()
        val wrapper = ContextWrapper(nonActivityContext)

        assertNull(nonActivityContext.findActivity())
        assertNull(wrapper.findActivity())
    }

    @Test
    fun findFragmentActivity_returnsFragmentActivityWhenPresent() {
        val fragmentActivity = Robolectric.buildActivity(FragmentActivity::class.java).get()
        val wrapper = ContextWrapper(fragmentActivity)

        assertSame(fragmentActivity, fragmentActivity.findFragmentActivity())
        assertSame(fragmentActivity, wrapper.findFragmentActivity())
    }

    @Test
    fun findFragmentActivity_returnsNullWhenOnlyComponentActivity() {
        val componentActivity = Robolectric.buildActivity(ComponentActivity::class.java).get()
        val wrapper = ContextWrapper(componentActivity)

        assertNull(componentActivity.findFragmentActivity())
        assertNull(wrapper.findFragmentActivity())
    }

    @Test
    fun findAppCompatActivity_returnsAppCompatActivityWhenPresent() {
        val appCompatActivity = Robolectric.buildActivity(MainActivity::class.java).get()
        val wrapper = ContextWrapper(appCompatActivity)

        assertSame(appCompatActivity, appCompatActivity.findAppCompatActivity())
        assertSame(appCompatActivity, wrapper.findAppCompatActivity())
    }

    @Test
    fun findAppCompatActivity_returnsNullWhenOnlyComponentActivity() {
        val componentActivity = Robolectric.buildActivity(ComponentActivity::class.java).get()
        val wrapper = ContextWrapper(componentActivity)

        assertNull(componentActivity.findAppCompatActivity())
        assertNull(wrapper.findAppCompatActivity())
    }
}
