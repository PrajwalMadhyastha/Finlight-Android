// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/dao/TransactionFilterConstants.kt
// REASON: REFACTOR (Domain DAO Decomposition - Issue #237) - Extracted shared
// SQL filter constants used across Transaction domain DAOs.
//
// NOTE: These constants interpolate TransactionStatus and TransactionType DB
// string values at classload time. If database string representations of
// TransactionStatus or TransactionType change, these SQL constants must be
// reviewed and regression-tested.
// =================================================================================
package io.pm.finlight.data.db.dao

import io.pm.finlight.TransactionStatus
import io.pm.finlight.TransactionType

const val SQL_STATUS_ACTIVE = "status != '${TransactionStatus.DB_PENDING}' AND status != '${TransactionStatus.DB_SKIPPED}'"
const val SQL_T_STATUS_ACTIVE = "T.status != '${TransactionStatus.DB_PENDING}' AND T.status != '${TransactionStatus.DB_SKIPPED}'"
const val SQL_P_STATUS_ACTIVE = "P.status != '${TransactionStatus.DB_PENDING}' AND P.status != '${TransactionStatus.DB_SKIPPED}'"
const val SQL_T1_STATUS_ACTIVE = "T1.status != '${TransactionStatus.DB_PENDING}' AND T1.status != '${TransactionStatus.DB_SKIPPED}'"

const val SQL_STATUS_PENDING = "'${TransactionStatus.DB_PENDING}'"
const val SQL_STATUS_CONFIRMED = "'${TransactionStatus.DB_CONFIRMED}'"
const val SQL_STATUS_SKIPPED = "'${TransactionStatus.DB_SKIPPED}'"

const val SQL_EXPENSE = "'${TransactionType.DB_EXPENSE}'"
const val SQL_INCOME = "'${TransactionType.DB_INCOME}'"
const val SQL_TRANSFER = "'${TransactionType.DB_TRANSFER}'"
