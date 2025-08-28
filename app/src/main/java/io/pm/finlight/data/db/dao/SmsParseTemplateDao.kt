// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/dao/SmsParseTemplateDao.kt
// REASON: NEW FILE - This DAO provides the necessary methods to interact with
// the new `sms_parse_templates` table. It includes methods to insert new
// templates and retrieve all existing templates for the heuristic engine to analyze.
// =================================================================================
package io.pm.finlight

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object (DAO) for the SmsParseTemplate entity.
 */
@Dao
interface SmsParseTemplateDao {
    /**
     * Inserts a new template. If a template with the same signature already exists,
     * the insertion is ignored to avoid duplicates.
     * @param template The template to insert.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(template: SmsParseTemplate)

    /**
     * Retrieves all stored SMS parse templates.
     * This is used by the heuristic engine to find a potential match for a new SMS.
     * @return A list of all SmsParseTemplate objects.
     */
    @Query("SELECT * FROM sms_parse_templates")
    suspend fun getAllTemplates(): List<SmsParseTemplate>
}
