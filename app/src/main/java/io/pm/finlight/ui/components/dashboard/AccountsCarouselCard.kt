package io.pm.finlight.ui.components.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.pm.finlight.AccountWithBalance
import io.pm.finlight.CreditCardMetrics
import io.pm.finlight.isCreditCard
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.rememberSafeBankLogoPainter
import io.pm.finlight.ui.theme.getUtilizationColor
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AccountsCarouselCard(
    accounts: List<AccountWithBalance>,
    creditCardMetrics: Map<Int, CreditCardMetrics> = emptyMap(),
    navController: NavController,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate("account_list") }
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Accounts",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "View All",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(accounts, key = { it.account.id }) { account ->
                AccountItem(
                    account = account,
                    metrics = creditCardMetrics[account.account.id],
                    navController = navController,
                )
            }
        }
    }
}

@Composable
private fun AccountItem(
    account: AccountWithBalance,
    metrics: CreditCardMetrics? = null,
    navController: NavController,
) {
    val currencyFormat =
        remember {
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).apply {
                maximumFractionDigits = 0
            }
        }
    val isCreditCard = account.account.isCreditCard
    val creditLimit = account.account.creditLimit
    val hasLimit = isCreditCard && creditLimit != null && creditLimit > 0.0

    GlassPanel(
        modifier =
            Modifier
                .width(190.dp)
                .height(124.dp)
                .clickable { navController.navigate("account_detail/${account.account.id}") },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Image(
                painter = rememberSafeBankLogoPainter(account.account.name),
                contentDescription = "${account.account.name} Logo",
                modifier = Modifier.height(24.dp),
            )
            Column {
                Text(
                    text = account.account.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metrics != null) {
                    Spacer(Modifier.height(2.dp))
                    val progress = (metrics.utilizationPercentage / 100f).coerceIn(0f, 1f)
                    val progressColor = getUtilizationColor(metrics.utilizationPercentage)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Limit: ${currencyFormat.format(metrics.creditLimit)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = "${String.format(Locale.getDefault(), "%.0f", metrics.utilizationPercentage)}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = progressColor,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp)),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${currencyFormat.format(metrics.currentCycleSpend)} spent • ${metrics.daysRemainingInCycle}d left",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (hasLimit) {
                    Text(
                        text = "Limit: ${currencyFormat.format(creditLimit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = currencyFormat.format(account.balance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
