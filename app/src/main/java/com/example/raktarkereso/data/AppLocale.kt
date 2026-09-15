package com.example.raktarkereso.data

import java.util.Locale

/**
 * Shared locale used everywhere we lowercase/compare user-facing text.
 * Using this instead of the default locale ensures Hungarian accented
 * characters (á, é, í, ó, ö, ő, ú, ü, ű) are folded correctly and
 * consistently across search, duplicate detection, and sorting.
 */
val HU_LOCALE: Locale = Locale.forLanguageTag("hu")
