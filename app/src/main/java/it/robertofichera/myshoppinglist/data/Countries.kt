package it.robertofichera.myshoppinglist.data

import java.text.Collator
import java.util.Currency
import java.util.Locale

/** A country the currency can be taken from, named and symbolised in the reader's own language. */
data class CountryChoice(val code: String, val name: String, val currencySymbol: String)

/**
 * Every country the platform knows a currency for, ordered by name under [displayLocale]'s own
 * collation — a plain sort would file "Österreich" after "Zypern" on a German phone.
 * Currency depends only on the country, so one arbitrary locale per country is enough.
 */
fun currencyCountries(displayLocale: Locale): List<CountryChoice> =
    Locale.getAvailableLocales()
        .filter { it.country.length == 2 }
        .distinctBy { it.country }
        .mapNotNull { locale ->
            val currency = runCatching { Currency.getInstance(locale) }.getOrNull()
                ?: return@mapNotNull null
            val name = locale.getDisplayCountry(displayLocale)
            if (name.isEmpty() || name == locale.country) return@mapNotNull null
            CountryChoice(
                code = locale.country,
                name = name,
                currencySymbol = currency.getSymbol(displayLocale),
            )
        }
        .sortedWith(compareBy(Collator.getInstance(displayLocale)) { it.name })

/** The chosen country as the picker should show it, or null when the code names nothing usable. */
fun countryChoice(code: String, displayLocale: Locale): CountryChoice? =
    currencyCountries(displayLocale).firstOrNull { it.code == code }
