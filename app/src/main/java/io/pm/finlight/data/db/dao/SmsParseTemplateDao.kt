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
     * Inserts a new template. If a template with the same composite primary key
     * (signature + corrected name) already exists, it is ignored.
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
     * Retrieves all templates that match a given signature.
     * @param signature The signature to search for.
     * @return A list of matching SmsParseTemplate objects.
     */
    @Query("SELECT * FROM sms_parse_templates WHERE templateSignature = :signature")
    suspend fun getTemplatesBySignature(signature: String): List<SmsParseTemplate>


    /**
     * Deletes all SMS parse templates from the database.
     */
    @Query("DELETE FROM sms_parse_templates")
    suspend fun deleteAll()
}