package it.robertofichera.myshoppinglist.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The instant a reminder fires. [dateUtcMillis] is the picked day as Material's date picker
 * reports it, midnight UTC; the hour and minute are the user's, read in [zone].
 */
fun reminderAt(dateUtcMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long =
    Instant.ofEpochMilli(dateUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        .atTime(hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
