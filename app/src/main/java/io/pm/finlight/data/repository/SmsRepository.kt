// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/SmsRepository.kt
// REASON: Making the fallback SMS lookup more robust. Instead of an exact date
// match, we now find the SMS with the mathematically closest timestamp, which
// fixes the bug where original messages couldn't be found for rule creation.
// =================================================================================
package io.pm.finlight

import android.content.Context
import android.provider.Telephony
import android.util.Log

/**
 * A repository class dedicated to handling data operations related to SMS messages.
 * This abstracts the logic of querying the Android ContentResolver away from ViewModels or Workers.
 */
class SmsRepository(private val context: Context) {
    /**
     * Fetches all SMS messages from the device's inbox.
     * @return A list of SmsMessage objects.
     */
    fun fetchAllSms(startDate: Long?): List<SmsMessage> {
        val smsList = mutableListOf<SmsMessage>()
        // Define the columns we want to retrieve
        val projection =
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
            )
        val selection: String?
        val selectionArgs: Array<String>?

        if (startDate != null) {
            selection = "${Telephony.Sms.DATE} >= ?"
            selectionArgs = arrayOf(startDate.toString())
            Log.d("SmsRepository", "Querying SMS with start date: $startDate")
        } else {
            selection = null
            selectionArgs = null
            Log.d("SmsRepository", "Querying all SMS messages.")
        }

        // Query the SMS inbox, sorting by date in descending order
        val cursor =
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "date DESC",
            )

        cursor?.use {
            // Get column indices once for efficiency
            val idIndex = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                smsList.add(
                    SmsMessage(
                        id = it.getLong(idIndex),
                        sender = it.getString(addressIndex) ?: "Unknown",
                        body = it.getString(bodyIndex) ?: "",
                        date = it.getLong(dateIndex),
                    ),
                )
            }
        }
        return smsList
    }

    /**
     * Fetches a single SMS message by its ID or timestamp.
     * This function first attempts to find the SMS by its database _ID.
     * If that fails, it falls back to searching by the timestamp (date), providing a robust way to
     * find the original message for transactions created via different import paths.
     *
     * @param lookupValue The value to search for, which could be the SMS _ID or its timestamp.
     * @return An SmsMessage object if found, otherwise null.
     */
    fun getSmsDetailsById(lookupValue: Long): SmsMessage? {
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)

        // --- First Attempt: Query by the proper database _ID ---
        var selection = "${Telephony.Sms._ID} = ?"
        var selectionArgs = arrayOf(lookupValue.toString())

        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                return SmsMessage(
                    id = cursor.getLong(idIndex),
                    sender = cursor.getString(addressIndex) ?: "Unknown",
                    body = cursor.getString(bodyIndex) ?: "",
                    date = cursor.getLong(dateIndex),
                )
            }
        }

        // --- BUG FIX: A more robust fallback that finds the message with the closest timestamp ---
        // Instead of an exact match, which is fragile, this finds the SMS whose 'date'
        // is mathematically closest to the timestamp we stored.
        val sortOrder = "ABS(date - $lookupValue) ASC LIMIT 1"

        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null, // Selection is not needed, we use the sort order
            null, // Selection args are not needed
            sortOrder,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                return SmsMessage(
                    id = cursor.getLong(idIndex),
                    sender = cursor.getString(addressIndex) ?: "Unknown",
                    body = cursor.getString(bodyIndex) ?: "",
                    date = cursor.getLong(dateIndex),
                )
            }
        }

        // If neither query found a result, return null.
        return null
    }
}
