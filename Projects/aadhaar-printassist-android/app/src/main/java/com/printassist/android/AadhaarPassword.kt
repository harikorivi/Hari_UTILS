package com.printassist.android

import java.time.Year
import java.util.Locale

object AadhaarPassword {
    fun generate(name: String, year: String, currentYear: Int = Year.now().value): String {
        val normalized = name.filterNot { it.isWhitespace() }.uppercase(Locale.ROOT)
        require(normalized.isNotEmpty() && normalized.all { it in 'A'..'Z' || it == '.' || it == '-' || it == '\'' }) {
            "Enter the English name printed on Aadhaar."
        }
        require(year.matches(Regex("[0-9]{4}")) && year.toInt() in 1900..currentYear) {
            "Enter a valid four-digit birth year."
        }
        return normalized.take(4) + year
    }
}
