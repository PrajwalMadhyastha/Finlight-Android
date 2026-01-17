package io.pm.finlight.data.db

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * CI Tripwire Test to ensure Database Migrations are always tested.
 * 
 * This test parses:
 * 1. AppDatabase.kt -> to find the current `version = X`
 * 2. AppDatabaseMigrationTest.kt -> to find the highest tested migration `migrate...To(Y)`
 * 
 * If current database version > max tested migration version, this test FAILS.
 * This prevents merging DB changes without corresponding migration tests.
 */
class DatabaseVersionSyncTest {

    @Test
    fun ensureDatabaseVersionHasMigrationTests() {
        val dbVersion = getDatabaseVersion()
        val maxMigrationTestVersion = getMaxTestedMigrationVersion()

        println("Calculated DB Version: $dbVersion")
        println("Max Tested Migration Version: $maxMigrationTestVersion")

        assertTrue(
            """
            🚨 SAFETY CHECK FAILED 🚨
            Database version is $dbVersion, but the highest migration test found is only for version $maxMigrationTestVersion.
            
            You have bumped the database version but haven't added a migration test in 'AppDatabaseMigrationTest.kt'.
            
            Please:
            1. Write a test for migration ${maxMigrationTestVersion} -> $dbVersion
            2. Or if this is a fresh install without migration, verify 'AppDatabaseMigrationTest.kt' is up to date.
            """.trimIndent(),
            maxMigrationTestVersion >= dbVersion
        )
    }

    private fun getDatabaseVersion(): Int {
        val file = findFile("src/main/java/io/pm/finlight/data/db/AppDatabase.kt")
        val regex = Regex("""version\s*=\s*(\d+)""")
        
        return file.useLines { lines ->
            lines.mapNotNull { line ->
                regex.find(line)?.groupValues?.get(1)?.toInt()
            }.firstOrNull()
        } ?: -1.also { fail("Could not parse 'version' from AppDatabase.kt") }
    }

    private fun getMaxTestedMigrationVersion(): Int {
        val file = findFile("src/androidTest/java/io/pm/finlight/data/db/AppDatabaseMigrationTest.kt")
        val regex = Regex("""migrate\d+To(\d+)""")
        
        return file.useLines { lines ->
            lines.mapNotNull { line ->
                regex.find(line)?.groupValues?.get(1)?.toInt()
            }.maxOrNull()
        } ?: 0.also { fail("Could not find any migration tests in AppDatabaseMigrationTest.kt") }
    }

    private fun findFile(relativePath: String): File {
        // Try paths relative to user.dir (which might be project root or module root)
        val possiblePaths = listOf(
            relativePath,                // If dir is 'app'
            "app/$relativePath",         // If dir is project root
            "../app/$relativePath"       // If dir is somewhere else
        )

        for (path in possiblePaths) {
            val f = File(path)
            if (f.exists()) return f
        }
        
        fail("Could not find source file: $relativePath at location: ${File(".").absolutePath}")
        return File("")
    }
}
