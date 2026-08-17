package it.robertofichera.myshoppinglist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.robertofichera.myshoppinglist.R
import it.robertofichera.myshoppinglist.data.currencyCountries

/**
 * Picks the country whose currency prices use. [selected] is an ISO country code, or empty for
 * the phone's own — which leads the list, since it is what the app does until told otherwise.
 */
@Composable
fun CurrencyDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale
    val countries = remember(locale) { currencyCountries(locale) }
    val listState = rememberLazyListState()

    // Opening on the current choice beats scrolling past two hundred countries to find it.
    LaunchedEffect(countries) {
        val index = countries.indexOfFirst { it.code == selected }
        if (index >= 0) listState.scrollToItem(index + 1)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_currency)) },
        text = {
            LazyColumn(state = listState) {
                item {
                    CountryRow(
                        label = stringResource(R.string.currency_automatic),
                        trailing = "",
                        isSelected = selected.isEmpty(),
                        onClick = { onSelect("") },
                    )
                }
                items(countries, key = { it.code }) { country ->
                    CountryRow(
                        label = country.name,
                        trailing = country.currencySymbol,
                        isSelected = country.code == selected,
                        onClick = { onSelect(country.code) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CountryRow(
    label: String,
    trailing: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            trailing,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}
