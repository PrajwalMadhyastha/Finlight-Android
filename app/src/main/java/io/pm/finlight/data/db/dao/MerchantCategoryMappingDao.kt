// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/MerchantCategoryMappingDao.kt
// REASON: FEATURE - Added an `insertAll` function. This allows for the efficient
// batch insertion of multiple merchant-to-category mappings, which is a core
// requirement for the new "learn from CSV import" feature.
// =================================================================================
package io.pm.finlight

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object (DAO) for the MerchantCategoryMapping entity.
 */
@Dao
interface MerchantCategoryMappingDao {

    /**
     * Inserts a new merchant-category mapping. If a mapping for the merchant
     * already exists, it will be replaced with the new one.
     *
     * @param mapping The MerchantCategoryMapping object to insert or update.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: MerchantCategoryMapping)

    // --- NEW: Function to insert a list of mappings during CSV import ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<MerchantCategoryMapping>)

    /**
     * Retrieves the learned category ID for a given merchant name.
     *
     * @param parsedName The name of the merchant to look up.
     * @return The associated category ID (Int), or null if no mapping exists.
     */
    @Query("SELECT categoryId FROM merchant_category_mapping WHERE parsedName = :parsedName")
    suspend fun getCategoryIdForMerchant(parsedName: String): Int?
}
