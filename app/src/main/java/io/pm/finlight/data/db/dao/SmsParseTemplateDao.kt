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
     * Inserts a list of SMS parse templates.
     * @param templates The list of templates to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<SmsParseTemplate>)

    /**
     * Retrieves all stored SMS parse templates.
     * This is used by the heuristic engine to find a potential match for a new SMS.
     * @return A list of all SmsParseTemplate objects.
     */
    @Query("SELECT * FROM sms_parse_templates")
    suspend fun getAllTemplates(): List<SmsParseTemplate>

    /**
     * Deletes all SMS parse templates from the database.
     */
    @Query("DELETE FROM sms_parse_templates")
    suspend fun deleteAll()
}