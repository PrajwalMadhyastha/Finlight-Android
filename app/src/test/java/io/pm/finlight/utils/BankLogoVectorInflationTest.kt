package io.pm.finlight.utils

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.R
import io.pm.finlight.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class BankLogoVectorInflationTest {
    @Test
    fun `all 37 bank drawables in BankLogoHelper can be inflated programmatically without error`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logos = BankLogoHelper.ALL_BANK_LOGOS

        assertEquals("Expected exactly 37 bank and institution logos", 37, logos.size)

        for (resId in logos) {
            val resName = context.resources.getResourceEntryName(resId)
            val drawable = ContextCompat.getDrawable(context, resId)
            assertNotNull("Drawable '$resName' (id: $resId) must inflate without throwing an exception", drawable)
        }
    }

    @Test
    fun `every drawable in res drawable has all pathData lengths strictly under 30000 characters`() {
        val drawableDirs =
            listOf(
                File("src/main/res/drawable"),
                File("app/src/main/res/drawable"),
                File("../app/src/main/res/drawable"),
            )
        val drawableDir =
            drawableDirs.firstOrNull { it.exists() && it.isDirectory }
                ?: error("Could not locate res/drawable directory in paths: $drawableDirs")

        val xmlFiles = drawableDir.listFiles { _, name -> name.endsWith(".xml") }?.toList().orEmpty()
        assertTrue("Expected to find drawable XML files in ${drawableDir.absolutePath}", xmlFiles.isNotEmpty())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()

        for (file in xmlFiles) {
            val doc =
                try {
                    builder.parse(file)
                } catch (e: Exception) {
                    continue
                }

            val root = doc.documentElement ?: continue
            assertAllPathDataLengths(root, file)
        }
    }

    @Test
    fun `ic_boi_logo xml file size is strictly under 3KB and no individual path exceeds 1000 characters`() {
        val candidatePaths =
            listOf(
                File("src/main/res/drawable/ic_boi_logo.xml"),
                File("app/src/main/res/drawable/ic_boi_logo.xml"),
                File("../app/src/main/res/drawable/ic_boi_logo.xml"),
            )
        val boiFile = candidatePaths.firstOrNull { it.exists() } ?: error("Could not find ic_boi_logo.xml")

        val fileSize = boiFile.length()
        assertTrue(
            "ic_boi_logo.xml file size ($fileSize bytes) must be strictly under 3KB (3072 bytes)",
            fileSize < 3072,
        )

        val factory = DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(boiFile)
        val pathNodes = doc.getElementsByTagName("path")
        for (i in 0 until pathNodes.length) {
            val element = pathNodes.item(i) as Element
            val pathData =
                element.getAttribute("android:pathData").ifEmpty {
                    element.getAttributeNS("http://schemas.android.com/apk/res/android", "pathData")
                }
            assertTrue(
                "ic_boi_logo path index $i length (${pathData.length}) must not exceed 1000 characters",
                pathData.length <= 1000,
            )
        }
    }

    @Test
    fun `ic_cub_logo xml file has no individual path exceeding 5000 characters`() {
        val candidatePaths =
            listOf(
                File("src/main/res/drawable/ic_cub_logo.xml"),
                File("app/src/main/res/drawable/ic_cub_logo.xml"),
                File("../app/src/main/res/drawable/ic_cub_logo.xml"),
            )
        val cubFile = candidatePaths.firstOrNull { it.exists() } ?: error("Could not find ic_cub_logo.xml")

        val factory = DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(cubFile)
        val pathNodes = doc.getElementsByTagName("path")
        for (i in 0 until pathNodes.length) {
            val element = pathNodes.item(i) as Element
            val pathData =
                element.getAttribute("android:pathData").ifEmpty {
                    element.getAttributeNS("http://schemas.android.com/apk/res/android", "pathData")
                }
            assertTrue(
                "ic_cub_logo path index $i length (${pathData.length}) must not exceed 5000 characters",
                pathData.length <= 5000,
            )
        }
    }

    @Test
    fun `Bank of India and BOI account names map to ic_boi_logo`() {
        assertEquals(R.drawable.ic_boi_logo, BankLogoHelper.getLogoForAccount("Bank of India"))
        assertEquals(R.drawable.ic_boi_logo, BankLogoHelper.getLogoForAccount("Bank of India Savings"))
        assertEquals(R.drawable.ic_boi_logo, BankLogoHelper.getLogoForAccount("BOI"))
        assertEquals(R.drawable.ic_boi_logo, BankLogoHelper.getLogoForAccount("My BOI Account"))
    }

    @Test
    fun `Bank of Maharashtra and BOM account names map to ic_bom_logo`() {
        assertEquals(R.drawable.ic_bom_logo, BankLogoHelper.getLogoForAccount("Bank of Maharashtra"))
        assertEquals(R.drawable.ic_bom_logo, BankLogoHelper.getLogoForAccount("Maharashtra Bank"))
        assertEquals(R.drawable.ic_bom_logo, BankLogoHelper.getLogoForAccount("BOM Current"))
    }

    @Test
    fun `Bank of Baroda and BOB account names map to ic_bob_logo`() {
        assertEquals(R.drawable.ic_bob_logo, BankLogoHelper.getLogoForAccount("Bank of Baroda"))
        assertEquals(R.drawable.ic_bob_logo, BankLogoHelper.getLogoForAccount("BOB Savings Acct"))
    }

    @Test
    fun `Bandhan Bank account names map to ic_bandan_logo`() {
        assertEquals(R.drawable.ic_bandan_logo, BankLogoHelper.getLogoForAccount("Bandhan Bank"))
        assertEquals(R.drawable.ic_bandan_logo, BankLogoHelper.getLogoForAccount("Bandhan Bank Savings"))
    }

    @Test
    fun `City Union Bank and CUB account names map to ic_cub_logo`() {
        assertEquals(R.drawable.ic_cub_logo, BankLogoHelper.getLogoForAccount("City Union Bank"))
        assertEquals(R.drawable.ic_cub_logo, BankLogoHelper.getLogoForAccount("CUB Account"))
    }

    @Test
    fun `Central Bank of India and CBI account names map to ic_cbi_logo`() {
        assertEquals(R.drawable.ic_cbi_logo, BankLogoHelper.getLogoForAccount("Central Bank of India"))
        assertEquals(R.drawable.ic_cbi_logo, BankLogoHelper.getLogoForAccount("CBI Savings"))
    }

    private fun assertAllPathDataLengths(
        element: Element,
        file: File,
    ) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            if (attr.nodeName.contains("pathData")) {
                val length = attr.nodeValue.length
                assertTrue(
                    "File '${file.name}' attribute '${attr.nodeName}' has length $length which exceeds limit of 30,000 characters",
                    length < 30_000,
                )
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                assertAllPathDataLengths(child as Element, file)
            }
        }
    }
}
