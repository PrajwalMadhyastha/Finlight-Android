// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/dao/TransactionDao.kt
// REASON: REFACTOR (Domain DAO Decomposition - Issue #237) - Decomposed god-object
// TransactionDao into TransactionWriteDao, TransactionQueryDao,
// TransactionAnalyticsDao, and TransactionReimbursementDao.
// TransactionDao now acts as a composite interface combining the domain DAOs.
// =================================================================================
package io.pm.finlight

import androidx.room.Dao
import io.pm.finlight.data.db.dao.TransactionAnalyticsDao
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.dao.TransactionReimbursementDao
import io.pm.finlight.data.db.dao.TransactionWriteDao

@Dao
interface TransactionDao :
    TransactionWriteDao,
    TransactionQueryDao,
    TransactionAnalyticsDao,
    TransactionReimbursementDao
