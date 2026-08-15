package it.robertofichera.myshoppinglist.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import it.robertofichera.myshoppinglist.BuildConfig
import it.robertofichera.myshoppinglist.R
import it.robertofichera.myshoppinglist.data.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    productCount: Int,
    onShowQuantityChange: (Boolean) -> Unit,
    onShowPriceChange: (Boolean) -> Unit,
    onBudgetEnabledChange: (Boolean) -> Unit,
    onOpenProducts: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SwitchRow(
                title = stringResource(R.string.settings_show_quantity),
                subtitle = stringResource(R.string.settings_show_quantity_desc),
                checked = settings.showQuantity,
                onCheckedChange = onShowQuantityChange,
            )
            SwitchRow(
                title = stringResource(R.string.settings_show_price),
                subtitle = stringResource(R.string.settings_show_price_desc),
                checked = settings.showPrice,
                onCheckedChange = onShowPriceChange,
            )
            SwitchRow(
                title = stringResource(R.string.settings_budget),
                subtitle = stringResource(R.string.settings_budget_desc),
                checked = settings.budgetEnabled,
                onCheckedChange = onBudgetEnabledChange,
            )
            HorizontalDivider()
            NavigationRow(
                title = stringResource(R.string.products_title),
                subtitle = pluralStringResource(R.plurals.settings_product_count, productCount, productCount),
                onClick = onOpenProducts,
            )
            HorizontalDivider()
            val context = LocalContext.current
            val openUrl: (String) -> Unit = { url ->
                // A device with no browser would otherwise throw ActivityNotFoundException.
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            }
            val repositoryUrl = stringResource(R.string.repository_url)
            NavigationRow(
                title = stringResource(R.string.settings_source),
                subtitle = repositoryUrl,
                onClick = { openUrl(repositoryUrl) },
            )
            HorizontalDivider()
            // Next to the installed version, so the two read as a comparison.
            val releasesUrl = stringResource(R.string.releases_url)
            NavigationRow(
                title = stringResource(R.string.settings_latest_release),
                subtitle = releasesUrl,
                onClick = { openUrl(releasesUrl) },
            )
            HorizontalDivider()
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_version), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.format_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
