package io.pm.finlight.ui.common

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import io.pm.finlight.Account
import io.pm.finlight.Category

val CategorySaver: Saver<Category?, Any> =
    listSaver(
        save = { category ->
            if (category == null) {
                emptyList()
            } else {
                listOf(category.id, category.name, category.iconKey, category.colorKey)
            }
        },
        restore = { list ->
            if (list.isEmpty()) {
                null
            } else {
                Category(
                    id = list[0] as Int,
                    name = list[1] as String,
                    iconKey = list[2] as String,
                    colorKey = list[3] as String,
                )
            }
        },
    )

val AccountSaver: Saver<Account?, Any> =
    listSaver(
        save = { account ->
            if (account == null) {
                emptyList()
            } else {
                listOf(
                    account.id,
                    account.name,
                    account.type,
                    account.creditLimit?.toString() ?: "",
                    account.billingCycleDay ?: -1,
                )
            }
        },
        restore = { list ->
            if (list.isEmpty()) {
                null
            } else {
                val limitStr = list[3] as String
                val billingDay = list[4] as Int
                Account(
                    id = list[0] as Int,
                    name = list[1] as String,
                    type = list[2] as String,
                    creditLimit = limitStr.toDoubleOrNull(),
                    billingCycleDay = if (billingDay == -1) null else billingDay,
                )
            }
        },
    )
